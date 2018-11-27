package alien4cloud.paas.yorc.modifier;

import alien4cloud.paas.wf.util.WorkflowUtils;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.model.workflow.activities.AbstractWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.DelegateWorkflowActivity;
import org.alien4cloud.tosca.normative.constants.NormativeWorkflowNameConstants;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static alien4cloud.paas.plan.ToscaNodeLifecycleConstants.STARTED;

/**
 * Modifies a Topology containing Service Node templates, to remove delegate
 * operations on these Service Nodes from Workflows.
 * A Service Node Template being an Abstract Node Template, it doesn't support
 * delegate operations.
 */
@Slf4j
@Component(value = ServiceTopologyModifier.YORC_SERVICE_TOPOLOGY_MODIFIER_TAG)
public class ServiceTopologyModifier extends TopologyModifierSupport {

    public static final String YORC_SERVICE_TOPOLOGY_MODIFIER_TAG = "yorc-service-topology-modifier";

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.debug("Modifying Workflows on Service Node Templates in topology " +
                topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {

        // Names of workflows to modify if they contain delegate operations
        // on services
        Set<String> workflows = new HashSet<String>(Arrays.asList(
                NormativeWorkflowNameConstants.INSTALL,
                NormativeWorkflowNameConstants.UNINSTALL,
                NormativeWorkflowNameConstants.STOP,
                NormativeWorkflowNameConstants.START));

        for (Map.Entry<String, NodeTemplate> nodeTemplateEntry : topology.getNodeTemplates().entrySet()) {

            String nodeId = nodeTemplateEntry.getKey();
            NodeTemplate nodeTemplate = nodeTemplateEntry.getValue();

            if (nodeTemplate instanceof ServiceNodeTemplate) {

                for (String workflowName : workflows) {

                    removeServiceDelegateOperation(topology, nodeId, nodeTemplate, workflowName);
                }

                // Adding a step to show the service state as started,
                // or Alien4Cloud would show it forever as being installed
                WorkflowUtils.addStateStep(
                        topology.getWorkflows().get(NormativeWorkflowNameConstants.INSTALL),
                        nodeId,
                        STARTED);
            }

        }
    }

    private void removeServiceDelegateOperation(Topology topology, String nodeId, NodeTemplate nodeTemplate, String workflowName) {

        Workflow workflow = topology.getWorkflows().get(workflowName);
        if (workflow != null) {
            Set<String> stepsToRemove = new HashSet<String>();

            for (Map.Entry<String, WorkflowStep> stepEntry : workflow.getSteps().entrySet()) {
                String currentStepId = stepEntry.getKey();
                WorkflowStep step = stepEntry.getValue();
                if (WorkflowUtils.isNodeStep(step, nodeId)) {
                    AbstractWorkflowActivity activity = step.getActivity();
                    if (activity instanceof DelegateWorkflowActivity &&
                            workflowName.equals(((DelegateWorkflowActivity) activity).getDelegate())) {

                        // Re-wire the workflow to remove this delegate operation
                        // on a Service Node Template
                        for (String precederId : step.getPrecedingSteps()) {

                            WorkflowStep preceder = workflow.getSteps().get(precederId);
                            if (preceder.getOnSuccess() == null) {
                                preceder.setOnSuccess(new HashSet<String>());
                            }
                            preceder.removeFollowing(stepEntry.getKey());
                            preceder.addAllFollowings(step.getOnSuccess());
                        }

                        for (String follower : step.getOnSuccess()) {
                            WorkflowStep followerStep = workflow.getSteps().get(follower);
                            followerStep.getPrecedingSteps().remove(currentStepId);
                        }

                        // Old step will be removed outside the loop to avoid
                        // concurrent modifications
                        stepsToRemove.add(stepEntry.getKey());
                    }
                }
            }

            // Removing delegate steps on Service Node Template
            workflow.getSteps().keySet().removeAll(stepsToRemove);
        }
    }

}
