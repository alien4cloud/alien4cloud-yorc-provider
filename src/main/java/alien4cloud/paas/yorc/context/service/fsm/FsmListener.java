package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FsmListener extends StateMachineListenerAdapter<FsmStates, FsmEvents> {

	private String id;

	public FsmListener(String id) {
		this.id = id;
	}

	@Override
	public void stateChanged(State<FsmStates, FsmEvents> from, State<FsmStates, FsmEvents> to) {
		if (log.isDebugEnabled())
			log.debug(String.format("FSM %s changed state from %s to %s.", id, from.getId(), to.getId()));
	}

	@Override
	public void stateMachineStarted(StateMachine<FsmStates, FsmEvents> stateMachine) {
		if (log.isDebugEnabled())
			log.debug(String.format("FSM %s started.", id));
	}
}
