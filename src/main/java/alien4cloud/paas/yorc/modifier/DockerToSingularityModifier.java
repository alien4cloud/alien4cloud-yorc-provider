package alien4cloud.paas.yorc.modifier;

import static alien4cloud.paas.yorc.Versions.SLURM_CSAR_VERSION;
import static alien4cloud.utils.AlienUtils.safe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.editor.operations.nodetemplate.DeleteNodeOperation;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.definitions.ImplementationArtifact;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.AbstractTemplate;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.FunctionEvaluatorContext;
import org.alien4cloud.tosca.utils.InterfaceUtils;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import alien4cloud.model.common.Tag;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.paas.yorc.modifier.util.InputsHelper;
import alien4cloud.paas.yorc.modifier.util.PropertiesHelper;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.utils.PropertyUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(value = DockerToSingularityModifier.D2S_MODIFIER_TAG)
public class DockerToSingularityModifier extends TopologyModifierSupport {

    public static final String D2S_MODIFIER_TAG = "docker-to-singularity-modifier";

    public static final String A4C_D2S_MODIFIER_TAG = "a4c_docker-to-singularity-modifier";
    public static final String A4C_TYPES_APPLICATION_DOCKER_CONTAINER = "tosca.nodes.Container.Application.DockerContainer";

    public static final String SLURM_TYPES_CONTAINER_RUNTIME = "yorc.nodes.slurm.ContainerRuntime";
    public static final String SLURM_TYPES_CONTAINER_JOB_UNIT = "yorc.nodes.slurm.ContainerJobUnit";
    public static final String SLURM_TYPES_HOST_TO_CONTAINER_VOLUME = "yorc.nodes.slurm.HostToContainerVolume";
    public static final String SLURM_TYPES_SINGULARITY_JOB = "yorc.nodes.slurm.SingularityJob";

    public static final String A4C_RUNNABLE_INTERFACE_NAME = "tosca.interfaces.node.lifecycle.Runnable";
    public static final String A4C_SUBMIT_OPERATION_NAME = "submit";
    public static final String A4C_SLURM_JOB_IMAGE_ARTIFACT_TYPE = "yorc.artifacts.Deployment.SlurmJobImage";

    public static final String A4C_NODES_REPLACEMENT_CACHE_KEY = "a4c_docker2singularity_nodes_replacement_key";
    public static final String A4C_NODES_DEPENDS_ON_CACHE_KEY = "a4c_docker2singularity_nodes_dependsOn_key";

    @Override
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology {}", topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            context.getLog().error("Couldn't process " + A4C_D2S_MODIFIER_TAG);
            log.warn("Couldn't process {}", A4C_D2S_MODIFIER_TAG, e);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }

    }

    protected void doProcess(Topology topology, FlowExecutionContext context) {
        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        Map<String, NodeTemplate> replacementMap = Maps.newHashMap();
        context.getExecutionCache().put(A4C_NODES_REPLACEMENT_CACHE_KEY, replacementMap);

        Map<String, Set<String>> containersDependencies = Maps.newHashMap();
        context.getExecutionCache().put(A4C_NODES_DEPENDS_ON_CACHE_KEY, containersDependencies);

        // A function evaluator context will be usefull
        // FIXME: use topology inputs ?
        Map<String, AbstractPropertyValue> inputValues = Maps.newHashMap();
        FunctionEvaluatorContext functionEvaluatorContext = new FunctionEvaluatorContext(topology, inputValues);

        // replace all yorc.nodes.slurm.ContainerJobUnit by
        // yorc.nodes.slurm.SingularityJob
        Set<NodeTemplate> containerJobUnitNodes = this.getNodesOfType(context, topology,
                SLURM_TYPES_CONTAINER_JOB_UNIT, false);
        containerJobUnitNodes.forEach(nodeTemplate -> transformContainerJobUnit(csar, topology, context, nodeTemplate));

        // replace all yorc.nodes.slurm.ContainerJobUnit by
        // yorc.nodes.slurm.SingularityJob if not already hosted on a ContainerJobUnit
        Set<NodeTemplate> containerRuntimeNodes = this.getNodesOfType(context, topology,
                SLURM_TYPES_CONTAINER_RUNTIME, false);
        containerRuntimeNodes.forEach(nodeTemplate -> transformContainerRuntime(csar, topology, context, nodeTemplate));

        // replace all tosca.nodes.Container.Application.DockerContainer by
        // yorc.nodes.slurm.SingularityJob if hosted on a ContainerRuntime transformed
        // into a yorc.nodes.slurm.SingularityJob
        Set<NodeTemplate> containerNodes = this.getNodesOfType(context, topology,
                A4C_TYPES_APPLICATION_DOCKER_CONTAINER, true);
        containerNodes.forEach(
                nodeTemplate -> transformContainer(csar, topology, context, functionEvaluatorContext, nodeTemplate));

        // for each volume node, populate the 'volumes' property of the corresponding
        // deployment resource
        Set<NodeTemplate> volumeNodes = this.getNodesOfType(context, topology,
                SLURM_TYPES_HOST_TO_CONTAINER_VOLUME, true);
        volumeNodes.forEach(nodeTemplate -> transformContainerVolume(csar, topology, context, nodeTemplate));

        linkDependsOn(csar, context, topology, containersDependencies, replacementMap);

        // Remove replaced nodes
        safe(replacementMap.keySet()).forEach(nodeName -> removeNode(csar, topology, nodeName));
    }

    protected void linkDependsOn(Csar csar, FlowExecutionContext context,
            Topology topology,
            Map<String, Set<String>> containersDependencies, Map<String, NodeTemplate> replacementMap) {
        containersDependencies.forEach((source, targets) -> {
            boolean sourceReplaced = true;
            boolean targetReplaced = true;
            NodeTemplate sourceNode = replacementMap.get(source);
            if (sourceNode == null) {
                // not replaced in this modifier
                sourceReplaced = false;
                sourceNode = topology.getNodeTemplates().get(source);
            }
            for (String target : targets) {
                NodeTemplate targetNode = replacementMap.get(target);
                if (targetNode == null) {
                    // not replaced in this modifier
                    targetReplaced = false;
                    targetNode = topology.getNodeTemplates().get(target);
                }
                if (sourceReplaced || targetReplaced) {
                    addRelationshipTemplate(csar, topology, sourceNode, targetNode.getName(),
                        NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");
                }
            }
        });
    }

    protected void removeNode(Csar csar, Topology topology, String nodeName) {
        DeleteNodeOperation deleteNodeOperation = new DeleteNodeOperation();
        deleteNodeOperation.setNodeName(nodeName);
        deleteNodeProcessor.process(csar, topology, deleteNodeOperation);
    }

    protected void addToReplacementMap(FlowExecutionContext context, NodeTemplate initialNode,
            NodeTemplate replacementNode) {
        Map<String, NodeTemplate> replacementMap = (Map<String, NodeTemplate>) context.getExecutionCache()
                .get(A4C_NODES_REPLACEMENT_CACHE_KEY);
        replacementMap.put(initialNode.getName(), replacementNode);

    }

    protected String getTargetJobType() {
        return SLURM_TYPES_SINGULARITY_JOB;
    }

    protected String getTargetJobTypeVersion() {
        return SLURM_CSAR_VERSION;
    }

    /**
     * Replace this node of type ContainerJobUnit by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    protected void transformContainerJobUnit(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate) {
        NodeTemplate singularityNode = addNodeTemplate(context, csar, topology, nodeTemplate.getName() + "_Singularity",
                getTargetJobType(), getTargetJobTypeVersion());
        addToReplacementMap(context, nodeTemplate, singularityNode);
        setNodeTagValue(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from", nodeTemplate.getName());

    }

    /**
     * Replace this node of type ContainerRuntime by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    protected void transformContainerRuntime(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate) {

        NodeTemplate jobUnitNode = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, nodeTemplate,
                SLURM_TYPES_CONTAINER_JOB_UNIT);

        if (jobUnitNode == null) {
            log.debug("Ignoring ContainerRuntime node <{}> not hosted on a ContainerJobUnit", nodeTemplate.getName());
            return;
        }
        Map<String, NodeTemplate> replacementMap = (Map<String, NodeTemplate>) context.getExecutionCache()
                .get(A4C_NODES_REPLACEMENT_CACHE_KEY);
        NodeTemplate singularityNode = replacementMap.get(jobUnitNode.getName());
        String tagValue = getNodeTagValueOrNull(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from");
        tagValue += "," + nodeTemplate.getName();
        setNodeTagValue(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from", tagValue);
        // Mark as replaced by the singularity job
        addToReplacementMap(context, nodeTemplate, singularityNode);

        transformContainerRuntimeLimits(csar, topology, context, nodeTemplate, singularityNode);

    }

    protected void transformContainerRuntimeLimits(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate containerRuntime, NodeTemplate singularityNode) {

        Optional<Capability> capOpt = safe(containerRuntime.getCapabilities()).values().stream()
                .filter(cap -> cap.getType().equals("org.alien4cloud.extended.container.capabilities.ApplicationHost"))
                .findFirst();
        if (!capOpt.isPresent()) {
            return;
        }
        Map<String, AbstractPropertyValue> properties = safe(capOpt.get().getProperties());

        AbstractPropertyValue numCpus = PropertyUtil.getPropertyValueFromPath(properties, "num_cpus");
        if (numCpus instanceof ScalarPropertyValue) {
            String sValue = ((ScalarPropertyValue) numCpus).getValue();
            if (sValue != null) {
                float value = Float.parseFloat(sValue);
                ScalarPropertyValue cpuPerTask = new ScalarPropertyValue(Integer.toString(Math.round(value)));
                setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.cpus_per_task", cpuPerTask);
            }
        }
        AbstractPropertyValue memSize = PropertyUtil.getPropertyValueFromPath(properties, "mem_size");
        if (memSize != null) {
            setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.mem_per_node", memSize);
        }
    }

    /**
     * Replace this node of type HostToContainerVolume by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    protected void transformContainerVolume(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate) {

        // FIXME : doesn't support many attachement (1 volume -> many containers) ?)
        Optional<RelationshipTemplate> relationshipTemplate = TopologyNavigationUtil
                .getTargetRelationships(nodeTemplate, "attachment").stream().findFirst();
        if (!relationshipTemplate.isPresent()) {
            log.debug("Ignoring DockerExtVolume node <{}> not linked to a Container", nodeTemplate.getName());
            return;
        }
        Map<String, NodeTemplate> replacementMap = (Map<String, NodeTemplate>) context.getExecutionCache()
                .get(A4C_NODES_REPLACEMENT_CACHE_KEY);
        NodeTemplate targetContainer = topology.getNodeTemplates().get(relationshipTemplate.get().getTarget());
        NodeTemplate singularityNode = replacementMap.get(targetContainer.getName());
        String tagValue = getNodeTagValueOrNull(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from");
        tagValue += "," + nodeTemplate.getName();
        setNodeTagValue(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from", tagValue);
        // Mark as replaced by the singularity job
        addToReplacementMap(context, nodeTemplate, singularityNode);

        String cPath = null;
        String hPath = null;
        AbstractPropertyValue containerPath = relationshipTemplate.get().getProperties().get("container_path");
        if (containerPath instanceof ScalarPropertyValue) {
            cPath = ((ScalarPropertyValue) containerPath).getValue();
        }
        AbstractPropertyValue hostPath = nodeTemplate.getProperties().get("path");
        if (hostPath instanceof ScalarPropertyValue) {
            hPath = ((ScalarPropertyValue) hostPath).getValue();
        }
        if (hPath == null || cPath == null) {
            return;
        }
        boolean readOnly = false;
        AbstractPropertyValue readOnlyVal = nodeTemplate.getProperties().get("readOnly");
        if (readOnlyVal instanceof ScalarPropertyValue) {
            readOnly = Boolean.parseBoolean(((ScalarPropertyValue) readOnlyVal).getValue());
        }

        // FIXME(loicalbertin) check that they are actual paths (prevent injecting code)

        transformVolumeProperties(csar, topology, context, singularityNode, hPath, cPath, readOnly);
    }

    protected void transformVolumeProperties(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate singularityNode, String hostPath, String containerPath, boolean readOnly) {

        String mountDirective = "--bind=" + hostPath + ":" + containerPath;
        if (readOnly) {
            mountDirective += ":ro";
        }
        ListPropertyValue cmdOpts = new ListPropertyValue(Lists.newArrayList());
        cmdOpts.getValue().add(mountDirective);
        addToListPropertyValue(csar, topology, context, singularityNode, "singularity_command_options", cmdOpts);
    }

    public static void setNodeTagValue(AbstractTemplate template, String name, String value) {
        List<Tag> tags = template.getTags();
        if (tags == null) {
            tags = Lists.newArrayList();
            template.setTags(tags);
        }
        Optional<Tag> ot = tags.stream().filter(t -> t.getName().equals(name)).findFirst();
        if (ot.isPresent()) {
            ot.get().setValue(value);
        } else {
            tags.add(new Tag(name, value));
        }
    }

    /**
     * Replace this node of type DockerContainer by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    private void transformContainer(Csar csar, Topology topology, FlowExecutionContext context,
            FunctionEvaluatorContext functionEvaluatorContext, NodeTemplate nodeTemplate) {

        NodeTemplate jobUnitNode = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, nodeTemplate,
                SLURM_TYPES_CONTAINER_JOB_UNIT);

        if (jobUnitNode == null) {
            log.debug("Ignoring DockerContainer node <{}> not hosted on a ContainerJobUnit", nodeTemplate.getName());
            return;
        }

        Map<String, NodeTemplate> replacementMap = (Map<String, NodeTemplate>) context.getExecutionCache()
                .get(A4C_NODES_REPLACEMENT_CACHE_KEY);
        NodeTemplate singularityNode = replacementMap.get(jobUnitNode.getName());
        String tagValue = getNodeTagValueOrNull(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from");
        tagValue += "," + nodeTemplate.getName();
        setNodeTagValue(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from", tagValue);
        // Mark as replaced by the singularity job
        addToReplacementMap(context, nodeTemplate, singularityNode);

        setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.name",
                new ScalarPropertyValue(singularityNode.getName()));

        transformContainerOperation(csar, context, functionEvaluatorContext, topology, nodeTemplate, singularityNode);
        transformContainerProperties(csar, topology, context, nodeTemplate, singularityNode);

        Map<String, Set<String>> containersDependencies = (Map<String, Set<String>>) context.getExecutionCache()
                .get(A4C_NODES_DEPENDS_ON_CACHE_KEY);
        Set<NodeTemplate> dependents = TopologyNavigationUtil.getSourceNodesByRelationshipType(topology, nodeTemplate,
                NormativeRelationshipConstants.DEPENDS_ON);
        dependents.forEach(sourceNode -> {
            containersDependencies.computeIfAbsent(sourceNode.getName(), k-> Sets.newHashSet()).add(nodeTemplate.getName());
        });
        Set<NodeTemplate> dependsOn =TopologyNavigationUtil.getTargetNodes(topology, nodeTemplate, "dependency");
        for (NodeTemplate targetNode : dependsOn) {
            containersDependencies.computeIfAbsent(nodeTemplate.getName(), k-> Sets.newHashSet()).add(targetNode.getName());
        }
    }

    protected void transformContainerOperation(Csar csar, FlowExecutionContext context,
            FunctionEvaluatorContext functionEvaluatorContext, Topology topology, NodeTemplate container,
            NodeTemplate singularityNode) {
        Operation op = getContainerImageOperation(container);
        if (op == null) {
            context.getLog()
                    .error("Container image is missing on standard.create operation of <" + container.getName() + ">");
            return;
        }

        createContainerOperation(csar, context, functionEvaluatorContext, topology, container, singularityNode, op);

        transformContainerInputs(csar, context, topology, container, singularityNode, functionEvaluatorContext,
                safe(op.getInputParameters()));

    }

    protected void createContainerOperation(Csar csar, FlowExecutionContext context,
            FunctionEvaluatorContext functionEvaluatorContext, Topology topology, NodeTemplate container,
            NodeTemplate singularityNode, Operation op) {
        Interface runnable = new Interface(A4C_RUNNABLE_INTERFACE_NAME);
        ImplementationArtifact dockerImageImpl = new ImplementationArtifact(
                "docker://" + op.getImplementationArtifact().getArtifactRef());
        dockerImageImpl.setArtifactRepository(op.getImplementationArtifact().getArtifactRepository());
        dockerImageImpl.setArchiveName(csar.getName());
        dockerImageImpl.setArchiveVersion(csar.getVersion());
        dockerImageImpl.setRepositoryName(op.getImplementationArtifact().getRepositoryName());
        dockerImageImpl.setRepositoryURL(op.getImplementationArtifact().getRepositoryURL());
        dockerImageImpl.setRepositoryCredential(op.getImplementationArtifact().getRepositoryCredential());
        dockerImageImpl.setArtifactType(A4C_SLURM_JOB_IMAGE_ARTIFACT_TYPE);
        Operation submit = new Operation(dockerImageImpl);
        runnable.setOperations(new HashMap<>());
        runnable.getOperations().put(A4C_SUBMIT_OPERATION_NAME, submit);
        singularityNode.setInterfaces(new HashMap<>());
        singularityNode.getInterfaces().put(A4C_RUNNABLE_INTERFACE_NAME, runnable);
    }

    protected void transformContainerInputs(Csar csar, FlowExecutionContext context, Topology topology,
            NodeTemplate container, NodeTemplate singularityNode, FunctionEvaluatorContext functionEvaluatorContext,
            Map<String, IValue> inputParameters) {
        inputParameters.forEach((inputName, iValue) -> {
            if (iValue instanceof AbstractPropertyValue) {
                AbstractPropertyValue v = InputsHelper.resolveInput(topology, container, functionEvaluatorContext,
                        inputName, (AbstractPropertyValue) iValue, context);
                if (v != null) {
                    String serializedValue = PropertyUtil.serializePropertyValue(v);
                    if (inputName.startsWith("ENV_")) {
                        String envKey = inputName.substring(4);
                        ListPropertyValue lpv = new ListPropertyValue(new ArrayList<>());
                        lpv.getValue().add(envKey + "=" + serializedValue);
                        addToListPropertyValue(csar, topology, context, singularityNode, "execution_options.env_vars",
                                lpv);
                        context.getLog().info("Env variable <" + envKey + "> for container <" + container.getName()
                                + "> set to value <" + serializedValue + ">");
                    } else if (inputName.startsWith("ARG_")) {
                        ListPropertyValue lpv = new ListPropertyValue(new ArrayList<>());
                        lpv.getValue().add(PropertyUtil.serializePropertyValue(v));
                        addToListPropertyValue(csar, topology, context, singularityNode, "execution_options.args", lpv);
                        context.getLog().info("Argument variable <" + inputName + "> for container <"
                                + container.getName() + "> set to value <" + serializedValue + ">");
                    }
                } else {
                    context.log()
                            .warn("Not able to define value for input <" + inputName + "> ("
                                    + PropertiesHelper.serializePropertyValue((AbstractPropertyValue) iValue)
                                    + ") of container <" + container.getName() + ">");
                }
            } else {
                context.log()
                        .warn("Input <" + inputName + "> of container <" + container.getName()
                                + "> is ignored since it's not of type AbstractPropertyValue but "
                                + iValue.getClass().getSimpleName());
            }
        });
    }

    public static Operation getContainerImageOperation(NodeTemplate nodeTemplate) {
        Operation imageOperation = InterfaceUtils.getOperationIfArtifactDefined(nodeTemplate.getInterfaces(),
                ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.CREATE);
        if (imageOperation != null) {
            return imageOperation;
        }
        // if not overriden in the template, fetch from the type.
        NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
        imageOperation = InterfaceUtils.getOperationIfArtifactDefined(nodeType.getInterfaces(),
                ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.CREATE);
        return imageOperation;
    }

    protected void transformContainerProperties(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate container, NodeTemplate singularityNode) {
        Map<String, AbstractPropertyValue> properties = container.getProperties();
        if (properties == null) {
            return;
        }
        transformContainerCommand(csar, topology, context, properties, singularityNode);
        transformContainerEnv(csar, topology, context, properties, singularityNode);
    }

    private void transformContainerCommand(Csar csar, Topology topology, FlowExecutionContext context,
            Map<String, AbstractPropertyValue> properties, NodeTemplate singularityNode) {

        AbstractPropertyValue dockerRunCmdProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_run_cmd");
        if (dockerRunCmdProp != null) {
            // Should be both of the same type "scalar"
            setNodePropertyPathValue(csar, topology, singularityNode, "execution_options.command", dockerRunCmdProp);
        }
        AbstractPropertyValue dockerRunArgsProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_run_args");
        if (dockerRunArgsProp instanceof ListPropertyValue) {
            // Should be both of the same type "List"
            addToListPropertyValue(csar, topology, context, singularityNode, "execution_options.args",
                    (ListPropertyValue) dockerRunArgsProp);
        }
    }

    protected void transformContainerEnv(Csar csar, Topology topology, FlowExecutionContext context,
            Map<String, AbstractPropertyValue> properties, NodeTemplate singularityNode) {
        AbstractPropertyValue dockerEnvVarsProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_env_vars");
        if (dockerEnvVarsProp instanceof ComplexPropertyValue) {
            // Convert map to list of string in k=v form
            ComplexPropertyValue mapProps = (ComplexPropertyValue) dockerEnvVarsProp;
            ListPropertyValue singEnvVarsProp = new ListPropertyValue(new ArrayList<>());
            for (Entry<String, Object> varEntry : safe(mapProps.getValue()).entrySet()) {
                singEnvVarsProp.getValue().add(varEntry.getKey() + "=" + varEntry.getValue().toString());
            }
            addToListPropertyValue(csar, topology, context, singularityNode, "execution_options.env_vars",
                    singEnvVarsProp);
        }
    }

    protected void addToListPropertyValue(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate, String propertyName, ListPropertyValue listValue) {
        appendOrPrependToListPropertyValue(csar, topology, context, nodeTemplate, propertyName, listValue, true);
    }

    protected void prependToListPropertyValue(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate, String propertyName, ListPropertyValue listValue) {
        appendOrPrependToListPropertyValue(csar, topology, context, nodeTemplate, propertyName, listValue, false);
    }

    private void appendOrPrependToListPropertyValue(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate, String propertyName, ListPropertyValue listValue, boolean append) {
        List<Object> mergedList = new ArrayList<>();
        if (!append) {
            mergedList.addAll(listValue.getValue());
        }
        AbstractPropertyValue existingPropValue = PropertyUtil
                .getPropertyValueFromPath(safe(nodeTemplate.getProperties()), propertyName);
        if (existingPropValue instanceof ListPropertyValue) {
            mergedList.addAll(safe(((ListPropertyValue) existingPropValue).getValue()));
        }
        if (append) {
            mergedList.addAll(listValue.getValue());
        }
        listValue.setValue(mergedList);
        setNodePropertyPathValue(csar, topology, nodeTemplate, propertyName, listValue);
    }

}