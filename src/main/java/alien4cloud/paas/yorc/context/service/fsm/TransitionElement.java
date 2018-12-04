package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.statemachine.action.Action;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
class TransitionElement {

	@Getter
	private FsmStates source;

	@Getter
	private FsmStates target;

	@Getter
	private DeploymentMessages inputEvent;

	@Getter
	private Action<FsmStates, DeploymentMessages> action;
}
