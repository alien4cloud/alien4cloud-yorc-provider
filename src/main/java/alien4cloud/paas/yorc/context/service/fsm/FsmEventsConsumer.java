package alien4cloud.paas.yorc.context.service.fsm;

import java.util.function.Consumer;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FsmEventsConsumer implements Consumer<Event> {

	@Inject
	private StateMachineService service;

	private void handle(FsmEvent event) {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Received an event of %s", event));
		}
		service.talk(event);
	}

	@Override
	public void accept(Event event) {
		if (event instanceof FsmEvent) {
			// Take action if the event is of the type specific to state machine
			handle((FsmEvent) event);
		}
	}
}
