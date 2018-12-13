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
                throw new Exception("Event Mapping Not handled");
        }

        return MessageBuilder
            .withPayload(payload)
            .setHeader("event",event)
            .build();
    }

     /**
     * A mapping between Yorc deployment status and the Fsm input events
     * @param status
     * @return
     */
    public static FsmEvents fromYorcToFsmEvent(String status) {
        switch (status.toUpperCase()) {
            case "DEPLOYED":
                return FsmEvents.DEPLOYMENT_SUCCESS;
            case "UNDEPLOYED":
                return FsmEvents.UNDEPLOYMENT_SUCCESS;
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                return FsmEvents.DEPLOYMENT_IN_PROGRESS;
            case "UNDEPLOYMENT_IN_PROGRESS":
                return FsmEvents.UNDEPLOYMENT_STARTED;
//		case "INITIAL":
//			return DEPLOYMENT_STARTED;
            case "DEPLOYMENT_FAILED":
            case "UNDEPLOYMENT_FAILED":
                return FsmEvents.FAILURE;
            default:
                return FsmEvents.FAILURE; //TODO should add an unknown state
        }
    }

    public static FsmStates fromYorcToFsmState(String status) {
        switch(status) {
            case "DEPLOYED":
                return FsmStates.DEPLOYED;
            case "UNDEPLOYED":
                return FsmStates.UNDEPLOYED;
            case "INIT_DEPLOYMENT":
                return FsmStates.DEPLOYMENT_INIT;
            case "DEPLOYMENT_IN_PROGRESS":
                return FsmStates.DEPLOYMENT_IN_PROGRESS;
            default:
            case "FAILURE":
                return FsmStates.FAILED;

        }
    }
}
