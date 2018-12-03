package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.statemachine.action.Action;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
class TransitionElement {

	@Getter
	private DeploymentStates source;

	@Getter
	private DeploymentStates target;

	@Getter
	private DeploymentMessages inputEvent;

	@Getter
	private Action<DeploymentStates, DeploymentMessages> action;
}
