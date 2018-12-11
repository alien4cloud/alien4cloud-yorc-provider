package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Component
@Scope("prototype")
@ToString
/**
 * The events specific to FsmBuilder
 */
public class FsmEvent extends Event {

	/**
	 * Input events to the state machine
	 */
	public enum DeploymentMessages {
		DEPLOYMENT_STARTED,
		DEPLOYMENT_SUBMITTED,
		DEPLOYMENT_IN_PROGRESS,
		DEPLOYMENT_SUCCESS,
		UNDEPLOYMENT_STARTED,
		UNDEPLOYMENT_SUCCESS,
		FAILURE,
	}

	/**
	 /**
	 * A mapping between Yorc deployment status and the Fsm input events
	 * @param status
	 * @return
	 */
	private static DeploymentMessages getDeploymentMessages(String status) {
		switch (status.toUpperCase()) {
		case "DEPLOYED":
			return DeploymentMessages.DEPLOYMENT_SUCCESS;
		case "UNDEPLOYED":
			return DeploymentMessages.UNDEPLOYMENT_SUCCESS;
		case "DEPLOYMENT_IN_PROGRESS":
		case "SCALING_IN_PROGRESS":
			return DeploymentMessages.DEPLOYMENT_IN_PROGRESS;
		case "UNDEPLOYMENT_IN_PROGRESS":
			return DeploymentMessages.UNDEPLOYMENT_STARTED;
//		case "INITIAL":
//			return DeploymentMessages.DEPLOYMENT_STARTED;
		case "DEPLOYMENT_FAILED":
		case "UNDEPLOYMENT_FAILED":
			return DeploymentMessages.FAILURE;
		default:
			return DeploymentMessages.FAILURE; //TODO should add an unknown state
		}
	}

	@Getter
	@Setter
	private DeploymentMessages message;

	@Getter
	@Setter
	private Map<String, Object> headers;

	public FsmEvent(String deploymentId, DeploymentMessages message, Map<String, Object> headers) {
		setDeployment_id(deploymentId);
		this.message = message;
		this.headers = headers;
		setType(EVT_DEPLOYMENT);
	}

	public FsmEvent(String deploymentId, String status) {
		setDeployment_id(deploymentId);
		setType(EVT_DEPLOYMENT);
		setMessage(getDeploymentMessages(status));
		setHeaders(ImmutableMap.of());
	}
}
