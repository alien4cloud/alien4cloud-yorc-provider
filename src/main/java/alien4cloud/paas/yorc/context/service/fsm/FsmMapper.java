package alien4cloud.paas.yorc.context.service.fsm;

import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.normative.constants.NormativeWorkflowNameConstants;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
public class FsmMapper {

    private FsmMapper() {
    }

    /**
     * When changing this, you should also consider changing {@link #map(Event)}.
     */
    public static boolean shouldMap(Event event) {
        switch (event.getType()) {
            case Event.EVT_DEPLOYMENT:
                return true;
            case Event.EVT_WORKFLOW:
                if (event.getWorkflowId().equals(NormativeWorkflowNameConstants.POST_UPDATE)) {
                    // since we manage post_update workflow here, we need to handle such kind of events
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
                payload = fromYorcWorkflowToFsmEvent(event.getStatus());
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
    private static FsmEvents fromYorcWorkflowToFsmEvent(String status) throws Exception {
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
            case "INITIAL":
                return FsmStates.DEPLOYMENT_INIT;
            case "DEPLOYMENT_IN_PROGRESS":
                return FsmStates.DEPLOYMENT_IN_PROGRESS;
            case "DEPLOYMENT_FAILED":
                return FsmStates.FAILED;
            case "UPDATE_IN_PROGRESS":
                return FsmStates.UPDATE_IN_PROGRESS;
            case "UPDATED":
                return FsmStates.UPDATED;
            case "UPDATE_FAILURE":
                return FsmStates.UPDATE_FAILED;
            default:
                throw new Exception(String.format("Unknown status from Yorc: %s", status));

        }
    }

}
