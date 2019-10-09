package alien4cloud.paas.yorc.modifier.util;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ConcatPropertyValue;
import org.alien4cloud.tosca.model.definitions.PropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.utils.FunctionEvaluator;
import org.alien4cloud.tosca.utils.FunctionEvaluatorContext;

import alien4cloud.utils.PropertyUtil;

/**
 * InputsHelper
 */
public class InputsHelper {

    /**
     * Resolve an input of a given nodeTemplate
     * @param topology The {@code Topology} holding the given {@code NodeTemplate}
     * @param nodeTemplate The given {@code NodeTemplate} holding the input
     * @param functionEvaluatorContext The {@code FunctionEvaluatorContext} used to evaluate functions
     * @param inputName the input name
     * @param iValue the value of this input
     * @param context A {@code FlowExecutionContext} coming from Topologies Modifiers
     * @return The resolved {@code AbstractPropertyValue} or {@code null}
     */
    public static AbstractPropertyValue resolveInput(Topology topology, NodeTemplate nodeTemplate,
            FunctionEvaluatorContext functionEvaluatorContext, String inputName, AbstractPropertyValue iValue,
            FlowExecutionContext context) {
        if (iValue instanceof ConcatPropertyValue) {
            ConcatPropertyValue cpv = (ConcatPropertyValue) iValue;
            StringBuilder sb = new StringBuilder();
            for (AbstractPropertyValue param : cpv.getParameters()) {
                AbstractPropertyValue v = resolveInput(topology, nodeTemplate, functionEvaluatorContext, inputName,
                        param, context);
                if (v instanceof ScalarPropertyValue) {
                    sb.append(PropertyUtil.getScalarValue(v));
                } else {
                    context.getLog()
                            .warn("Some element in concat operation for input <" + inputName + "> ("
                                    + PropertiesHelper.serializePropertyValue(param) + ") of container <"
                                    + nodeTemplate.getName() + "> resolved to a complex result. Let's ignore it.");
                }
            }
            return new ScalarPropertyValue(sb.toString());
        }
        if (PropertiesHelper.isTargetedEndpointProperty(topology, nodeTemplate, iValue)) {
            return PropertiesHelper.getTargetedEndpointProperty(topology, nodeTemplate, iValue);
        }

        try {
            AbstractPropertyValue propertyValue = FunctionEvaluator.tryResolveValue(functionEvaluatorContext,
                    nodeTemplate, nodeTemplate.getProperties(), iValue);
            if (propertyValue != null) {
                if (propertyValue instanceof PropertyValue) {
                    return propertyValue;
                } else {
                    context.getLog()
                            .warn("Property is not PropertyValue but <" + propertyValue.getClass() + "> for input <"
                                    + inputName + "> (" + PropertiesHelper.serializePropertyValue(propertyValue)
                                    + ") of container <" + nodeTemplate.getName() + ">");
                }
            }
        } catch (IllegalArgumentException iae) {
            context.getLog()
                    .warn("Can't resolve value for input <" + inputName + "> ("
                            + PropertiesHelper.serializePropertyValue(iValue) + ") of container <"
                            + nodeTemplate.getName() + ">, error was : " + iae.getMessage());
        }
        return null;
    }
}