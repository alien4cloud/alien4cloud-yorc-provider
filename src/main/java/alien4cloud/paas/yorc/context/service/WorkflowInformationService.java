package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.*;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

@Slf4j
@Service
public class WorkflowInformationService {

    @Inject
    private YorcOrchestrator orchestrator;

    @Inject
    private DeployementRegistry registry;

    public void onEvent(Event event) {
        switch(event.getType()) {
            case Event.EVT_WORKFLOW:
                processWorkflowEvent(event);
                break;
            case Event.EVT_WORKFLOWSTEP:
                processWorkflowStepEvent(event);
                break;
            case Event.EVT_ALIENTASK:
                processTaskEvent(event);
                break;
            case Event.EVT_DEPLOYMENT:
                processDeploymentEvent(event);
            default:
        }
    }

    private void processDeploymentEvent(Event event) {
            PaaSDeploymentStatusMonitorEvent a4cEvent = new PaaSDeploymentStatusMonitorEvent();
            a4cEvent.setDeploymentId(registry.toAlienId(event.getDeploymentId()));
            a4cEvent.setDeploymentStatus(YorcOrchestrator.getDeploymentStatusFromString(event.getStatus()));
            orchestrator.postAlienEvent(a4cEvent);
    }

    private void processTaskEvent(Event event) {
        switch (event.getStatus()) {
            case "initial":
                postTaskEvent(event,new TaskSentEvent());
                break;
            case "running":
                postTaskEvent(event,new TaskStartedEvent());
                break;
            case "done":
                postTaskEvent(event,new TaskSucceededEvent());
                break;
            case "failed":
                // TODO:
            default:
        }
    }

    private void processWorkflowStepEvent(Event event) {
        switch(event.getStatus()) {
            case "initial":
                postWorkflowStepEvent(event,new WorkflowStepStartedEvent());
                break;
            case "done":
                postWorkflowStepEvent(event,new WorkflowStepCompletedEvent());
                break;
            case "failed":
                // TODO: to be done
                break;
            default:
        }
    }

    private void processWorkflowEvent(Event event) {
        switch(event.getStatus()) {
            case "initial":
                // -> PaasWorkflowStartedEvent
                PaaSWorkflowStartedEvent a4cEvent = new PaaSWorkflowStartedEvent();
                a4cEvent.setWorkflowName(event.getWorkflowId());
                postWorkflowEvent(event,a4cEvent);
                break;
            case "done":
                postWorkflowEvent(event,new PaaSWorkflowSucceededEvent());
                break;
            case "failed":
                postWorkflowEvent(event,new PaaSWorkflowFailedEvent());
                break;
            default:
        }
    }

    private void postWorkflowEvent(Event yorcEvent,AbstractPaaSWorkflowMonitorEvent a4cEvent) {
        a4cEvent.setWorkflowId(yorcEvent.getWorkflowId());
        a4cEvent.setExecutionId(yorcEvent.getAlienExecutionId());
        a4cEvent.setDeploymentId(registry.toAlienId(yorcEvent.getDeploymentId()));
        orchestrator.postAlienEvent(a4cEvent);
    }

    private void postWorkflowStepEvent(Event yorcEvent,AbstractWorkflowStepEvent a4cEvent) {
        a4cEvent.setOperationName(yorcEvent.getOperationName());
        a4cEvent.setInstanceId(yorcEvent.getInstanceId());
        a4cEvent.setNodeId(yorcEvent.getNodeId());
        a4cEvent.setStepId(yorcEvent.getStepId());

        postWorkflowEvent(yorcEvent,a4cEvent);
    }

    private void postTaskEvent(Event yorcEvent,AbstractTaskEvent a4cEvent) {
        a4cEvent.setOperationName(yorcEvent.getOperationName());
        a4cEvent.setTaskId(yorcEvent.getAlienTaskId());
        a4cEvent.setWorkflowStepId(yorcEvent.getStepId());
        a4cEvent.setInstanceId(yorcEvent.getInstanceId());
        a4cEvent.setNodeId(yorcEvent.getNodeId());

        postWorkflowEvent(yorcEvent,a4cEvent);
    }

}
