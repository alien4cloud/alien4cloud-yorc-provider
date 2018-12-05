package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Component
@Scope("prototype")
@ToString
/**
 * The events specific to FsmConfiguration
 */
public class FsmEvent extends Event {

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
}
