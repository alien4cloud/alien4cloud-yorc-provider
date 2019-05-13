package alien4cloud.paas.yorc.context.service.fsm;

import alien4cloud.paas.yorc.context.rest.response.Event;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class FsmMapper {

    private FsmMapper() {
    }

    public static boolean shouldMap(Event event) {
        switch (event.getType()) {
            case Event.EVT_DEPLOYMENT:
                return true;
            default:
                return false;
        }
    }

    public static Message<FsmEvents> map(Event event) throws Exception {
        FsmEvents payload;

        switch(event.getType()) {
            case Event.EVT_DEPLOYMENT:
                payload = fromYorcToFsmEvent(event.getStatus());
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
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                return FsmEvents.DEPLOYMENT_IN_PROGRESS;
            case "UNDEPLOYMENT_IN_PROGRESS":
                return FsmEvents.UNDEPLOYMENT_STARTED;
            case "DEPLOYMENT_FAILED":
            case "UNDEPLOYMENT_FAILED":
                return FsmEvents.FAILURE;
            default:
                throw new Exception(String.format("Unknown status from Yorc: %s", status));
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
            default:
                throw new Exception(String.format("Unknown status from Yorc: %s", status));

        }
    }

}
