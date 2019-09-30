package alien4cloud.paas.yorc.modifier.util;

import java.util.Set;

import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.FunctionPropertyValue;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;

import alien4cloud.tosca.serializer.ToscaPropertySerializerUtils;
import alien4cloud.utils.PropertyUtil;

/**
 * PropertiesHelper
 */
public class PropertiesHelper {

    /**
     * Serialize a given {@code AbstractPropertyValue} into a string
     *
     * @param value the value to serialize
     * @return value serialized into a string
     */
    public static String serializePropertyValue(AbstractPropertyValue value) {
        try {
            return ToscaPropertySerializerUtils.formatPropertyValue(0, value);
        } catch (Exception e) {
            return "Serialization not possible : " + e.getMessage();
        }
    }

    /**
     * For a given node template, if the inputParameterValue value is a function if
     * of type get_attribute(TARGET, requirement, property) and the target is a
     * docker container, return true if the targeted capability has this property.
     */
    public static boolean isTargetedEndpointProperty(Topology topology, NodeTemplate sourceNodeTemplate,
            IValue inputParameterValue) {
        AbstractPropertyValue abstractPropertyValue = getTargetedEndpointProperty(topology, sourceNodeTemplate,
                inputParameterValue);
        return abstractPropertyValue != null;
    }

    /**
     * For a given node template, if the inputParameterValue value is a function if
     * of type get_attribute(TARGET, requirement, property) and the target is a
     * docker container, return the value of the property.
     */
    public static AbstractPropertyValue getTargetedEndpointProperty(Topology topology, NodeTemplate sourceNodeTemplate,
            IValue inputParameterValue) {
        // a get_attribute that searchs an ip_address on a requirement that targets a
        // Docker Container should return true
        if (inputParameterValue instanceof FunctionPropertyValue) {
            FunctionPropertyValue evaluatedFunction = (FunctionPropertyValue) inputParameterValue;
            if (evaluatedFunction.getFunction().equals(ToscaFunctionConstants.GET_ATTRIBUTE)
                    && evaluatedFunction.getTemplateName().equals(ToscaFunctionConstants.R_TARGET)) {
                String requirement = evaluatedFunction.getCapabilityOrRequirementName();
                if (requirement != null) {
                    Set<RelationshipTemplate> targetRelationships = TopologyNavigationUtil
                            .getTargetRelationships(sourceNodeTemplate, requirement);
                    for (RelationshipTemplate targetRelationship : targetRelationships) {
                        NodeTemplate targetNode = topology.getNodeTemplates().get(targetRelationship.getTarget());
                        Capability endpoint = targetNode.getCapabilities()
                                .get(targetRelationship.getTargetedCapabilityName());
                        String attributeName = "capabilities." + targetRelationship.getTargetedCapabilityName() + "."
                                + evaluatedFunction.getElementNameToFetch();
                        if (targetNode instanceof ServiceNodeTemplate
                                && ((ServiceNodeTemplate) targetNode).getAttributeValues().containsKey(attributeName)) {
                            return new ScalarPropertyValue(
                                    ((ServiceNodeTemplate) targetNode).getAttributeValues().get(attributeName));
                        } else {
                            AbstractPropertyValue targetPropertyValue = PropertyUtil.getPropertyValueFromPath(
                                    endpoint.getProperties(), evaluatedFunction.getElementNameToFetch());
                            if (targetPropertyValue != null) {
                                return targetPropertyValue;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}