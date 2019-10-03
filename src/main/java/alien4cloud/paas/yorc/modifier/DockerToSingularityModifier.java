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

    private void doProcess(Topology topology, FlowExecutionContext context) {
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
        Set<NodeTemplate> containerJobUnitNodes = TopologyNavigationUtil.getNodesOfType(topology,
                SLURM_TYPES_CONTAINER_JOB_UNIT, false);
        containerJobUnitNodes.forEach(nodeTemplate -> transformContainerJobUnit(csar, topology, context, nodeTemplate));

        // replace all yorc.nodes.slurm.ContainerJobUnit by
        // yorc.nodes.slurm.SingularityJob if not already hosted on a ContainerJobUnit
        Set<NodeTemplate> containerRuntimeNodes = TopologyNavigationUtil.getNodesOfType(topology,
                SLURM_TYPES_CONTAINER_RUNTIME, false);
        containerRuntimeNodes.forEach(nodeTemplate -> transformContainerRuntime(csar, topology, context, nodeTemplate));

        // replace all tosca.nodes.Container.Application.DockerContainer by
        // yorc.nodes.slurm.SingularityJob if hosted on a ContainerRuntime transformed
        // into a yorc.nodes.slurm.SingularityJob
        Set<NodeTemplate> containerNodes = TopologyNavigationUtil.getNodesOfType(topology,
                A4C_TYPES_APPLICATION_DOCKER_CONTAINER, true);
        containerNodes.forEach(
                nodeTemplate -> transformContainer(csar, topology, context, functionEvaluatorContext, nodeTemplate));

        // for each volume node, populate the 'volumes' property of the corresponding
        // deployment resource
        Set<NodeTemplate> volumeNodes = TopologyNavigationUtil.getNodesOfType(topology,
                SLURM_TYPES_HOST_TO_CONTAINER_VOLUME, true);
        volumeNodes.forEach(nodeTemplate -> transformContainerVolume(csar, topology, context, nodeTemplate));

        linkDependsOn(csar, context, topology, containersDependencies, replacementMap);

        // Remove replaced nodes
        safe(replacementMap.keySet()).forEach(nodeName -> removeNode(csar, topology, nodeName));
    }

    private void linkDependsOn(Csar csar, FlowExecutionContext context, Topology topology,
            Map<String, Set<String>> containersDependencies, Map<String, NodeTemplate> replacementMap) {
        containersDependencies.forEach((source, targets) -> {
            NodeTemplate sourceNode = replacementMap.get(source);
            safe(targets).forEach(target -> {
                NodeTemplate targetNode = replacementMap.get(target);
                addRelationshipTemplate(csar, topology, sourceNode, targetNode.getName(),
                        NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");
            });
        });
    }

    private void removeNode(Csar csar, Topology topology, String nodeName) {
        DeleteNodeOperation deleteNodeOperation = new DeleteNodeOperation();
        deleteNodeOperation.setNodeName(nodeName);
        deleteNodeProcessor.process(csar, topology, deleteNodeOperation);
    }

    private void addToReplacementMap(FlowExecutionContext context, NodeTemplate initialNode,
            NodeTemplate replacementNode) {
        Map<String, NodeTemplate> replacementMap = (Map<String, NodeTemplate>) context.getExecutionCache()
                .get(A4C_NODES_REPLACEMENT_CACHE_KEY);
        replacementMap.put(initialNode.getName(), replacementNode);

    }

    /**
     * Replace this node of type ContainerJobUnit by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    private void transformContainerJobUnit(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate) {
        NodeTemplate singularityNode = addNodeTemplate(csar, topology, nodeTemplate.getName() + "_Singularity",
                SLURM_TYPES_SINGULARITY_JOB, SLURM_CSAR_VERSION);
        addToReplacementMap(context, nodeTemplate, singularityNode);
        setNodeTagValue(singularityNode, A4C_D2S_MODIFIER_TAG + "_created_from", nodeTemplate.getName());

    }

    /**
     * Replace this node of type ContainerRuntime by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    private void transformContainerRuntime(Csar csar, Topology topology, FlowExecutionContext context,
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



    }

    /**
     * Replace this node of type HostToContainerVolume by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    private void transformContainerVolume(Csar csar, Topology topology, FlowExecutionContext context,
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

        String cPath=null;
        String hPath=null;
        AbstractPropertyValue containerPath =  relationshipTemplate.get().getProperties().get("container_path");
        if (containerPath instanceof ScalarPropertyValue){
            cPath= ((ScalarPropertyValue)containerPath).getValue();
        }
        AbstractPropertyValue hostPath = nodeTemplate.getProperties().get("path");
        if (hostPath instanceof ScalarPropertyValue){
            hPath= ((ScalarPropertyValue)hostPath).getValue();
        }
        if(hPath==null || cPath==null) {
            return;
        }
        String mountDirective = "--bind="+hPath+":"+cPath;
        AbstractPropertyValue readOnlyVal = nodeTemplate.getProperties().get("readOnly");
        if (readOnlyVal instanceof ScalarPropertyValue) {
            boolean readOnly= Boolean.parseBoolean(((ScalarPropertyValue)readOnlyVal).getValue());
            if (readOnly) {
                mountDirective+=":ro";
            }
        }

        ListPropertyValue cmdOpts = new ListPropertyValue(Lists.newArrayList());
        // FIXME(loicalbertin) check that they are actual paths (prevent injecting code)
        cmdOpts.getValue().add(mountDirective);
        addToSingularityCmdOptions(csar, topology, context, singularityNode, cmdOpts);

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

        setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.name", new ScalarPropertyValue(singularityNode.getName()));

        transformContainerOperation(csar, context, functionEvaluatorContext, topology, nodeTemplate, singularityNode);
        transformContainerProperties(csar, topology, context, nodeTemplate, singularityNode);

        Map<String, Set<String>> containersDependencies = (Map<String, Set<String>>) context.getExecutionCache()
                .get(A4C_NODES_DEPENDS_ON_CACHE_KEY);
        Set<NodeTemplate> dependents = TopologyNavigationUtil.getSourceNodesByRelationshipType(topology, nodeTemplate,
                NormativeRelationshipConstants.DEPENDS_ON);
        dependents.forEach(sourceNode -> {
            Set<String> d = containersDependencies.get(sourceNode.getName());
            if (d == null) {
                d = Sets.newHashSet();
                containersDependencies.put(sourceNode.getName(), d);
            }
            d.add(nodeTemplate.getName());
        });
    }

    private void transformContainerOperation(Csar csar, FlowExecutionContext context,
            FunctionEvaluatorContext functionEvaluatorContext, Topology topology, NodeTemplate container,
            NodeTemplate singularityNode) {
        Operation op = getContainerImageOperation(container);
        if (op == null) {
            context.getLog()
                    .error("Container image is missing on standard.create operation of <" + container.getName() + ">");
            return;
        }

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

        transformContainerInputs(csar, context, topology, container, singularityNode, functionEvaluatorContext,
                safe(op.getInputParameters()), submit);

    }

    private void transformContainerInputs(Csar csar, FlowExecutionContext context, Topology topology,
            NodeTemplate container, NodeTemplate singularityNode, FunctionEvaluatorContext functionEvaluatorContext,
            Map<String, IValue> inputParameters, Operation targetOperation) {
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
                        addToSingularityEnvVars(csar, topology, context, singularityNode, lpv);
                        context.getLog().info("Env variable <" + envKey + "> for container <" + container.getName()
                                + "> set to value <" + serializedValue + ">");
                    } else if (inputName.startsWith("ARG_")) {
                        ListPropertyValue lpv = new ListPropertyValue(new ArrayList<>());
                        lpv.getValue().add(PropertyUtil.serializePropertyValue(v));
                        addToSingularityCmdArgs(csar, topology, context, singularityNode, lpv);
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

    private void transformContainerProperties(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate container, NodeTemplate singularityNode) {
        Map<String, AbstractPropertyValue> properties = container.getProperties();
        if (properties == null) {
            return;
        }
        transformContainerCommand(csar, topology, context, properties, singularityNode);
        transformContainerEnv(csar, topology, context, properties, singularityNode);
        transformContainerLimits(csar, topology, context, properties, singularityNode);
    }

    private void transformContainerLimits(Csar csar, Topology topology, FlowExecutionContext context,
            Map<String, AbstractPropertyValue> properties, NodeTemplate singularityNode) {

        AbstractPropertyValue cpu_share = PropertyUtil.getPropertyValueFromPath(properties, "cpu_share");
        if (cpu_share instanceof ScalarPropertyValue) {
            String sValue = ((ScalarPropertyValue) cpu_share).getValue();
            if (sValue != null) {
                float value = Float.parseFloat(sValue);
                // 1024 is = to one CPU
                value /= 1024;
                ScalarPropertyValue cpuPerTask = new ScalarPropertyValue(
                        Integer.toString(Math.max(Math.round(value), 1)));
                setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.cpus_per_task", cpuPerTask);
            }
        }
        AbstractPropertyValue cpu_share_limit = PropertyUtil.getPropertyValueFromPath(properties, "cpu_share_limit");
        if (cpu_share_limit instanceof ScalarPropertyValue) {
            String sValue = ((ScalarPropertyValue) cpu_share_limit).getValue();
            if (sValue != null) {
                float value = Float.parseFloat(sValue);
                // 1024 is = to one CPU
                value /= 1024;
                ScalarPropertyValue cpuPerTask = new ScalarPropertyValue(
                        Integer.toString(Math.max(Math.round(value), 1)));
                setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.cpus_per_task", cpuPerTask);
            }
        }
        AbstractPropertyValue mem_share = PropertyUtil.getPropertyValueFromPath(properties, "mem_share");
        if (mem_share != null) {
            setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.mem_per_node", mem_share);
        }
        AbstractPropertyValue mem_share_limit = PropertyUtil.getPropertyValueFromPath(properties, "mem_share_limit");
        if (mem_share_limit != null) {
            setNodePropertyPathValue(csar, topology, singularityNode, "slurm_options.mem_per_node", mem_share_limit);
        }
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
            addToSingularityCmdArgs(csar, topology, context, singularityNode, (ListPropertyValue) dockerRunArgsProp);
        }
    }

    private void transformContainerEnv(Csar csar, Topology topology, FlowExecutionContext context,
            Map<String, AbstractPropertyValue> properties, NodeTemplate singularityNode) {
        AbstractPropertyValue dockerEnvVarsProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_env_vars");
        if (dockerEnvVarsProp instanceof ComplexPropertyValue) {
            // Convert map to list of string in k=v form
            ComplexPropertyValue mapProps = (ComplexPropertyValue) dockerEnvVarsProp;
            ListPropertyValue singEnvVarsProp = new ListPropertyValue(new ArrayList<>());
            for (Entry<String, Object> varEntry : safe(mapProps.getValue()).entrySet()) {
                singEnvVarsProp.getValue().add(varEntry.getKey() + "=" + varEntry.getValue().toString());
            }
            addToSingularityEnvVars(csar, topology, context, singularityNode, singEnvVarsProp);
        }
    }

    private void addToSingularityEnvVars(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate singularityNode, ListPropertyValue envVars) {
        List<Object> mergedList = new ArrayList<>();
        AbstractPropertyValue singEnvVarsProp = PropertyUtil
                .getPropertyValueFromPath(safe(singularityNode.getProperties()), "execution_options.env_vars");
        if (singEnvVarsProp instanceof ListPropertyValue) {
            mergedList.addAll(safe(((ListPropertyValue) singEnvVarsProp).getValue()));
        }
        mergedList.addAll(envVars.getValue());
        envVars.setValue(mergedList);
        setNodePropertyPathValue(csar, topology, singularityNode, "execution_options.env_vars", envVars);
    }

    private void addToSingularityCmdArgs(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate singularityNode, ListPropertyValue cmdArgs) {
        List<Object> mergedList = new ArrayList<>();
        AbstractPropertyValue singCmdArgsProp = PropertyUtil
                .getPropertyValueFromPath(safe(singularityNode.getProperties()), "execution_options.args");
        if (singCmdArgsProp instanceof ListPropertyValue) {
            mergedList.addAll(safe(((ListPropertyValue) singCmdArgsProp).getValue()));
        }
        mergedList.addAll(cmdArgs.getValue());
        cmdArgs.setValue(mergedList);
        setNodePropertyPathValue(csar, topology, singularityNode, "execution_options.args", cmdArgs);
    }

    private void addToSingularityCmdOptions(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate singularityNode, ListPropertyValue cmdOpts) {
        List<Object> mergedList = new ArrayList<>();
        AbstractPropertyValue singCmdOptsProp = PropertyUtil
                .getPropertyValueFromPath(safe(singularityNode.getProperties()), "singularity_command_options");
        if (singCmdOptsProp instanceof ListPropertyValue) {
            mergedList.addAll(safe(((ListPropertyValue) singCmdOptsProp).getValue()));
        }
        mergedList.addAll(cmdOpts.getValue());
        cmdOpts.setValue(mergedList);
        setNodePropertyPathValue(csar, topology, singularityNode, "singularity_command_options", cmdOpts);
    }

}