package alien4cloud.paas.yorc.context.service.fsm;

import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.normative.constants.NormativeWorkflowNameConstants;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
public class FsmMapper {

    private static final String LOGS_MARK_BGN = "Status for deployment \"";
    private static final String LOGS_MARK_END = "\" changed to \"undeployed\"";

    private FsmMapper() {
    }

    /**
     * When changing this, you should also consider changing {@link #map(PaaSDeploymentLog)}.
     */
    public static boolean shouldMap(PaaSDeploymentLog logEvent) {
        if (logEvent.getWorkflowId() == null || !logEvent.getWorkflowId().equals("uninstall")) return false;
        if (logEvent.getContent()== null) return false;
        if (!logEvent.getContent().startsWith(LOGS_MARK_BGN)) return false;
        if (!logEvent.getContent().endsWith(LOGS_MARK_END)) return false;
        return true;
    }

    /**
     * When changing this, you should also consider changing {@link #shouldMap(PaaSDeploymentLog)}.
     */
    public static Message<FsmEvents>  map(PaaSDeploymentLog logEvent) throws Exception {
        FsmEvents payload = FsmEvents.LAST_LOG_RECEIVED;

        return MessageBuilder
                .withPayload(payload)
                .setHeader(StateMachineService.YORC_DEPLOYMENT_ID, logEvent.getDeploymentPaaSId())
                .build();
    }

    /**
     * When changing this, you should also consider changing {@link #map(Event)}.
     */
    public static boolean shouldMap(Event event) {
        switch (event.getType()) {
            case Event.EVT_DEPLOYMENT:
                return true;
            case Event.EVT_WORKFLOW:
                if (event.getWorkflowId().equals(NormativeWorkflowNameConstants.POST_UPDATE)
                        || event.getWorkflowId().equals(NormativeWorkflowNameConstants.PRE_UPDATE)) {
                    // since we manage post_update and pre_update workflow here, we need to handle such kind of events
                    return true;
                }
            default:
                return false;
        }
    }

    /**
     * When changing this, you should also consider changing {@link #shouldMap(Event)}.
     */
    public static Message<FsmEvents> map(Event event) throws Exception {
        FsmEvents payload;

        switch(event.getType()) {
            case Event.EVT_DEPLOYMENT:
                payload = fromYorcToFsmEvent(event.getStatus());
                break;
            case Event.EVT_WORKFLOW:
                payload = fromYorcWorkflowToFsmEvent(event.getWorkflowId(), event.getStatus());
                break;
            default:
                throw new Exception("Event mapping not handled");
        }

        return MessageBuilder
            .withPayload(payload)
            .setHeader(StateMachineService.YORC_DEPLOYMENT_ID, event.getDeploymentId())
            .build();
    }

     /**
     * A mapping between Yorc deployment status and the Fsm input events
     * @param status
     * @return
     */
    private static FsmEvents fromYorcToFsmEvent(String status) throws Exception {
        switch (status.toUpperCase()) {
            case "INITIAL":
                return FsmEvents.DEPLOYMENT_INIT;
            case "DEPLOYED":
                return FsmEvents.DEPLOYMENT_SUCCESS;
            case "UNDEPLOYED":
                return FsmEvents.UNDEPLOYMENT_SUCCESS;
            case "PURGED":
                return FsmEvents.DEPLOYMENT_PURGED;
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                return FsmEvents.DEPLOYMENT_IN_PROGRESS;
            case "UNDEPLOYMENT_IN_PROGRESS":
                return FsmEvents.UNDEPLOYMENT_STARTED;
            case "PURGE_IN_PROGRESS":
                return FsmEvents.UNDEPLOYMENT_STARTED;
            case "UPDATE_IN_PROGRESS":
                return FsmEvents.UPDATE_IN_PROGRESS;
            case "UPDATED":
                return FsmEvents.UPDATE_SUCCESS;
            case "DEPLOYMENT_FAILED":
            case "UNDEPLOYMENT_FAILED":
            case "UPDATE_FAILURE":
            case "PURGE_FAILED":
                return FsmEvents.FAILURE;
            default:
                throw new Exception(String.format("Unknown status from Yorc: %s", status));
        }
    }

    /**
     * A mapping between Yorc workflow event and the Fsm input events
     * @param status
     * @return
     */
    private static FsmEvents fromYorcWorkflowToFsmEvent(String workflowId, String status) throws Exception {
        switch (workflowId) {
            case NormativeWorkflowNameConstants.PRE_UPDATE:
                switch (status.toUpperCase()) {
                    case "INITIAL":
                        return FsmEvents.PRE_UPDATE_RUNNING;
                    case "RUNNING":
                        return FsmEvents.PRE_UPDATE_RUNNING;
                    case "DONE":
                        return FsmEvents.PRE_UPDATE_SUCCESS;
                    case "FAILED":
                        return FsmEvents.PRE_UPDATE_FAILURE;
                    case "CANCELED":
                        return FsmEvents.PRE_UPDATE_CANCELED;
                    default:
                        throw new Exception(String.format("Unknown workflow event from Yorc: %s", status));
                }
            case NormativeWorkflowNameConstants.POST_UPDATE:
                switch (status.toUpperCase()) {
                    case "INITIAL":
                        return FsmEvents.POST_UPDATE_IN_PROGRESS;
                    case "RUNNING":
                        return FsmEvents.POST_UPDATE_IN_PROGRESS;
                    case "DONE":
                        return FsmEvents.POST_UPDATE_SUCCESS;
                    case "FAILED":
                        return FsmEvents.POST_UPDATE_FAILURE;
                    case "CANCELED":
                        return FsmEvents.POST_UPDATE_CANCELED;
                    default:
                        throw new Exception(String.format("Unknown workflow event from Yorc: %s", status));
                }
            default:
                throw new Exception(String.format("Unknown workflowId from Yorc: %s", workflowId));
        }
    }

    /**
     * Convert Yorc deployment's state to the Fsm state
     * @param status Yorc deployment's state
     * @return Fsm state
     */
    public static FsmStates fromYorcToFsmState(String status) throws Exception {
        switch(status) {
            case "DEPLOYED":
                return FsmStates.DEPLOYED;
            case "UNDEPLOYMENT_IN_PROGRESS":
            case "UNDEPLOYED":
                // This is not an error. It means that the deployment has been undeployed without purging.
                // That is to say, the undeploy process has not yet finished.
                // So the plugin should continue the undeploy process, i.e., to purge the deployment.
                return FsmStates.UNDEPLOYMENT_IN_PROGRESS;
            case "PURGE_IN_PROGRESS":
                return FsmStates.UNDEPLOYMENT_PURGING;
            case "INITIAL":
                return FsmStates.DEPLOYMENT_INIT;
            case "DEPLOYMENT_IN_PROGRESS":
                return FsmStates.DEPLOYMENT_IN_PROGRESS;
            case "DEPLOYMENT_FAILED":
                return FsmStates.FAILED;
            case "UNDEPLOYMENT_FAILED":
                return FsmStates.UNDEPLOYMENT_FAILED;
            case "UPDATE_IN_PROGRESS":
                return FsmStates.UPDATE_IN_PROGRESS;
            case "UPDATED":
                return FsmStates.UPDATED;
            case "UPDATE_FAILURE":
                return FsmStates.UPDATE_FAILED;
            case "PURGE_FAILED":
                return FsmStates.PURGE_FAILED;
            default:
                throw new Exception(String.format("Unknown status from Yorc: %s", status));

        }
    }

}
