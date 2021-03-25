package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.transition.Transition;

@Slf4j
public class FsmListener extends StateMachineListenerAdapter<FsmStates, FsmEvents> {

	private String id;

	public FsmListener(String id) {
		this.id = id;
	}

	@Override
	public void transitionStarted(Transition<FsmStates,FsmEvents> transition) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("FSM[%s] (%s) -- %s --> (%s)",id,transition.getSource().getId(),transition.getTrigger().getEvent().name(),transition.getTarget().getId()));
		}
	}

	@Override
	public void eventNotAccepted(Message<FsmEvents> event) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("FSM[%s] Event %s not handled",id,event.getPayload().name()));
		}
	}

	@Override
	public void stateMachineStarted(StateMachine<FsmStates, FsmEvents> stateMachine) {
		if (log.isDebugEnabled())
			log.debug(String.format("FSM %s started.", id));
	}
}
