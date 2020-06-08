package alien4cloud.paas.yorc.modifier;

import alien4cloud.deployment.ArtifactProcessorService;
import alien4cloud.paas.wf.TopologyContext;
import alien4cloud.paas.wf.WorkflowSimplifyService;
import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.utils.CloneUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.definitions.*;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractToscaType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("gangja-resolver-modifier")
public class GangjaModifier extends TopologyModifierSupport {

    /**
     * The base type of datastore clients.
     */
    public static final String NODE_TYPE_TO_EXPORE = "tosca.nodes.Root";

    /**
     * The base type of relationship that are considered.
     */
    public static final String RELATIONSHIP_TYPE_TO_EXPORE = "org.alien4cloud.relationships.ConnectsToStaticEndpoint";

    /**
     * This is the name of the relationship property that stores the mapping between capability properties and variable names.
     */
    public static final String VAR_MAPPING_PROPERTY = "var_mapping";

    /**
     * This is the name of the node property that stores variable values.
     */
    public static final String VAR_VALUES_PROPERTY = "var_values";

    public static final String GANGJA_ARTIFACT_TYPE = "org.alien4cloud.artifacts.GangjaConfig";

    @Inject
    private WorkflowSimplifyService workflowSimplifyService;

    @Inject
    private WorkflowsBuilderService workflowBuilderService;

    @Inject
    private ArtifactProcessorService artifactProcessorService;

    private ThreadLocal<Pattern> jinja2varDetectionPattern = new ThreadLocal<Pattern>() {
        @Override
        protected Pattern initialValue() {
            return Pattern.compile("\\{\\{.*\\W?_\\.(\\w+)\\W?.*\\}\\}");
        }
    };

    @PostConstruct
    private void init() {

    }

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            log.warn("Can't process k8s-spark-jobs modifier:", e);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    protected void doProcess(Topology topology, FlowExecutionContext context) {
        if (log.isDebugEnabled()) {
            log.debug("ARM processing topology");
        }

        Set<NodeTemplate> nodes = TopologyNavigationUtil.getNodesOfType(topology, NODE_TYPE_TO_EXPORE, true, false);
        nodes.stream().forEach(nodeTemplate -> {
            // check if node has org.alien4cloud.artifacts.GangjaConfig artefacts
            if (hasGangjaFile(nodeTemplate)) {
                NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
                PropertyDefinition varValuesPropertyDefinition = nodeType.getProperties().get(VAR_VALUES_PROPERTY);
                if (varValuesPropertyDefinition != null
                        && varValuesPropertyDefinition.getType().equals("map")
                    /*&& varValuesPropertyDefinition.getEntrySchema().equals("string")*/) {
                    processNode(topology, nodeTemplate, context);
                } else {
                    context.log().warn(String.format("The node %s has artefact of type %s but no '%s' property, skipping it.", nodeTemplate.getName(), GANGJA_ARTIFACT_TYPE, VAR_VALUES_PROPERTY));
                }
            }
        });

    }

    private boolean hasGangjaFile(NodeTemplate nodeTemplate) {
        for (DeploymentArtifact deploymentArtifact : nodeTemplate.getArtifacts().values()) {
            if (deploymentArtifact.getArtifactType().equals(GANGJA_ARTIFACT_TYPE)) {
                return true;
            }
        }
        return false;
    }

    private void processNode(Topology topology, NodeTemplate nodeTemplate, FlowExecutionContext context) {
        if (log.isDebugEnabled()) {
            log.debug("Processing node {}", nodeTemplate.getName());
        }
        Set<RelationshipTemplate> relationships = TopologyNavigationUtil.getRelationshipsFromType(nodeTemplate, RELATIONSHIP_TYPE_TO_EXPORE);
        Map<String, Object> varValues = Maps.newHashMap();
        relationships.stream().forEach(relationshipTemplate -> {
            if (log.isDebugEnabled()) {
                log.debug("Processing relationship {}", relationshipTemplate.getName());
            }

            NodeTemplate targetNode = topology.getNodeTemplates().get(relationshipTemplate.getTarget());
            String targetCapability = relationshipTemplate.getTargetedCapabilityName();
            // find the var mapping property
            AbstractPropertyValue apv = relationshipTemplate.getProperties().get(VAR_MAPPING_PROPERTY);
            if (apv != null && apv instanceof ComplexPropertyValue) {
                Map<String, Object> mappingProperties = ((ComplexPropertyValue)apv).getValue();
                mappingProperties.forEach((propertyName, propertyValue) -> {
                    String varNames = propertyValue.toString();
                    // get the corresponding property value
                    IValue targetPropertyValue = getPropertyFromCapabilityOrNode(targetNode, targetCapability, propertyName);
                    if (targetPropertyValue != null && targetPropertyValue instanceof ScalarPropertyValue) {
                        String varValue = ((ScalarPropertyValue)targetPropertyValue).getValue();
                        // we accept CSV var names, so a capability property can be mapped to several variables
                        String[] varNamesArray = varNames.split(",");
                        for (String varName: varNamesArray) {
                            varValues.put(varName, varValue);
                        }
                    }
                });
            }
        });

        // here get all gangja artefact, resolv them, parse them, get the list of vars and check if all are satisfied
        for (Map.Entry<String, DeploymentArtifact> aa : nodeTemplate.getArtifacts().entrySet()) {
            if (aa.getValue().getArtifactType().equals(GANGJA_ARTIFACT_TYPE)) {
                DeploymentArtifact clonedArtifact = CloneUtil.clone(aa.getValue());
                artifactProcessorService.processDeploymentArtifact(clonedArtifact, topology.getId());
                Set<String> parsedVariables = parseFileAndExtractJinjaVariables(clonedArtifact.getArtifactPath());
                parsedVariables.stream().filter(s -> !varValues.containsKey(s)).forEach(s -> {
                    context.log().error(String.format("The config file <%s> for node <%s> contains a variable <%s> that can not be find in var mapping !", aa.getKey(), nodeTemplate.getName(), s));
                });
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Here are the var values: {}", varValues);
        }
        if (!varValues.isEmpty()) {
            // finally feed the var_values node property
            ComplexPropertyValue complexPropertyValue = new ComplexPropertyValue(varValues);
            nodeTemplate.getProperties().put(VAR_VALUES_PROPERTY, complexPropertyValue);
        }
    }

    private Set<String> parseFileAndExtractJinjaVariables(String path) {
        Set<String> result = Sets.newHashSet();
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));

            // For each line of input, try matching in it.
            String line;
            while ((line = r.readLine()) != null) {
                // For each match in the line, extract and print it.
                Matcher m = jinja2varDetectionPattern.get().matcher(line);
                while (m.find()) {
                    result.add(m.group(1));
                    int start = m.start(1);
                    int end = m.end(1);
                    line = line.substring(0, start) + line.substring(end);
                    m = jinja2varDetectionPattern.get().matcher(line);
                }
            }
        } catch (IOException e) {
            log.warn("Not able to parse file at {}", path);
        }
        return result;
    }

    /**
     * Look for the property value in the given capability properties, falling back into the the node properties.
     */
    private IValue getPropertyFromCapabilityOrNode(NodeTemplate nodeTemplate, String capabilityName, String propertyName) {
        IValue value = nodeTemplate.getCapabilities().get(capabilityName).getProperties().get(propertyName);
        if (value == null) {
            value = nodeTemplate.getProperties().get(propertyName);
            if (value == null) {
                value = nodeTemplate.getAttributes().get(propertyName);
                if (value == null) {
                    value = nodeTemplate.getAttributes().get("capabilities." + capabilityName + "." + propertyName);
                    if (value == null) {
                        value = nodeTemplate.getCapabilities().get(capabilityName).getProperties().get(propertyName);
                        if (value == null && nodeTemplate instanceof ServiceNodeTemplate) {
                            String attValue = ((ServiceNodeTemplate)nodeTemplate).getAttributeValues().get("capabilities." + capabilityName + "." + propertyName);
                            if (attValue != null) {
                                value = new ScalarPropertyValue(attValue);
                            } else {
                                attValue = ((ServiceNodeTemplate)nodeTemplate).getAttributeValues().get(propertyName);
                                if (attValue != null) {
                                    value = new ScalarPropertyValue(attValue);
                                }
                            }
                        }
                    }
                }
            }
            // TODO: also look for attribute value (including capability attribute notation)
        }
        return value;
    }
}
