package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Map;

import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

import alien4cloud.paas.model.DeploymentStatus;

@Component
public class StateMachineService {

	Map<String, StateMachine<DeploymentStatus, DeploymentMessages>> cache = Maps.newConcurrentMap() ;

	/**
	 * Send an event to the state machine to decide the next state
	 * @param event Input event
	 * @return Next state
	 */
	public DeploymentStatus sendEvent(DeploymentEvent event) {
		if (!cache.containsKey(event.getDeploymentId())) {
			cache.put(event.getDeploymentId(), FSM.buildMachine());
		}
		cache.get(event.getDeploymentId()).sendEvent(event.getMessage());
		return getState(event.getDeploymentId());
	}

	/**
	 * Get the state of given deployment
	 * @param deploymentId id of deployment
	 * @return Current state of this deployment
	 */
	public DeploymentStatus getState(String deploymentId) {
		if (!cache.containsKey(deploymentId)) {
			cache.put(deploymentId, FSM.buildMachine());
		}
		return cache.get(deploymentId).getState().getId();
	}

}
