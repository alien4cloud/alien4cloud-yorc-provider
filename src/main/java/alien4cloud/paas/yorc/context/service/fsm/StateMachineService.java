package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Map;

import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

@Component
public class StateMachineService {

	// TODO Problem of concurrency
	private Map<String, StateMachine<DeploymentStates, DeploymentMessages>> cache = Maps.newHashMap();

	/**
	 * Send an event to the state machine to decide the next state
	 * @param event Input event
	 * @return Next state
	 */
	public DeploymentStates sendEvent(DeploymentEvent event) {
		if (!cache.containsKey(event.getDeploymentId())) {
			cache.put(event.getDeploymentId(), FSM.buildMachine(DeploymentStates.UNDEPLOYED));
		}
		cache.get(event.getDeploymentId()).sendEvent(event.getMessage());
		return getState(event.getDeploymentId());
	}

	/**
	 * Get the state of given deployment
	 * @param deploymentId Id of deployment
	 * @return Current state of this deployment
	 */
	public DeploymentStates getState(String deploymentId) {
		if (!cache.containsKey(deploymentId)) {
			cache.put(deploymentId, FSM.buildMachine(DeploymentStates.UNDEPLOYED));
		}
		return cache.get(deploymentId).getState().getId();
	}

}
