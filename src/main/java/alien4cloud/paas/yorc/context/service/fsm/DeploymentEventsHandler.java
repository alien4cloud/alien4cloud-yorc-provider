package alien4cloud.paas.yorc.context.service.fsm;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class DeploymentEventsHandler {

	@Inject
	private StateMachineService service;

	public void listen(DeploymentEvent event) {
		service.sendEvent(event);
	}
}
