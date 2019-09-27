package alien4cloud.paas.yorc.modifier;

import static alien4cloud.paas.yorc.Versions.SLURM_CSAR_VERSION;
import static alien4cloud.utils.AlienUtils.safe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.editor.operations.nodetemplate.DeleteNodeOperation;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.ImplementationArtifact;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.InterfaceUtils;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.wf.validation.WorkflowValidator;
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
    public static final String SLURM_TYPES_SINGULARITY_JOB = "yorc.nodes.slurm.SingularityJob";

    public static final String A4C_RUNNABLE_INTERFACE_NAME = "tosca.interfaces.node.lifecycle.Runnable";
    public static final String A4C_SUBMIT_OPERATION_NAME = "submit";
    public static final String A4C_SLURM_JOB_IMAGE_ARTIFACT_TYPE = "yorc.artifacts.Deployment.SlurmJobImage";

    public static final String A4C_NODES_REPLACEMENT_CACHE_KEY = "a4c_docker2singularity_nodes_replacement_key";

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
        if (context.getEnvironmentContext().isPresent()) {
            csar.setName(csar.getName() + "-" + context.getEnvironmentContext().get().getEnvironment().getName());
        }

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
        containerNodes.forEach(nodeTemplate -> transformContainer(csar, topology, context, nodeTemplate));

        // Remove replaced nodes
        Map<String, NodeTemplate> replacementMap = (Map<String, NodeTemplate>) context.getExecutionCache()
                .get(A4C_NODES_REPLACEMENT_CACHE_KEY);

        safe(replacementMap.keySet()).forEach(nodeName -> removeNode(csar, topology, nodeName));
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
        if (replacementMap == null) {
            replacementMap = new HashMap<>();
            context.getExecutionCache().put(A4C_NODES_REPLACEMENT_CACHE_KEY, replacementMap);
        }
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

        // TODO take into account transformation
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

        // TODO take into account transformation

    }

    /**
     * Replace this node of type DockerContainer by a node of type
     * yorc.nodes.slurm.SingularityJob.
     */
    private void transformContainer(Csar csar, Topology topology, FlowExecutionContext context,
            NodeTemplate nodeTemplate) {

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

        // TODO take into account transformation

        transformContainerOperation(csar, context, nodeTemplate, singularityNode);
        transformContainerProperties(csar, topology, context, nodeTemplate, singularityNode);

    }

    private void transformContainerOperation(Csar csar, FlowExecutionContext context, NodeTemplate container,
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
    }

    private void transformContainerCommand(Csar csar, Topology topology, FlowExecutionContext context,
            Map<String, AbstractPropertyValue> properties, NodeTemplate singularityNode) {

        AbstractPropertyValue dockerRunCmdProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_run_cmd");
        if (dockerRunCmdProp != null) {
            // Should be both of the same type "scalar"
            setNodePropertyPathValue(csar, topology, singularityNode, "execution_options.command", dockerRunCmdProp);
        }
        AbstractPropertyValue dockerRunArgsProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_run_args");
        if (dockerRunArgsProp != null) {
            // Should be both of the same type "scalar"
            setNodePropertyPathValue(csar, topology, singularityNode, "execution_options.args", dockerRunArgsProp);
        }
    }
    private void transformContainerEnv(Csar csar, Topology topology, FlowExecutionContext context,
            Map<String, AbstractPropertyValue> properties, NodeTemplate singularityNode) {
                AbstractPropertyValue dockerEnvVarsProp = PropertyUtil.getPropertyValueFromPath(properties, "docker_env_vars");
                if (dockerEnvVarsProp instanceof ComplexPropertyValue) {
                    // Convert map to list of string in k=v form
                    ComplexPropertyValue mapProps = (ComplexPropertyValue)dockerEnvVarsProp;
                    ListPropertyValue singEnvVarsProp = new ListPropertyValue(new ArrayList<>());
                    for (Entry<String, Object> varEntry : safe(mapProps.getValue()).entrySet()) {
                        singEnvVarsProp.getValue().add(varEntry.getKey()+"="+varEntry.getValue().toString());
                    }
                    setNodePropertyPathValue(csar, topology, singularityNode, "execution_options.env_vars", singEnvVarsProp);
                }
            }
}