package alien4cloud.paas.yorc.context.service.fsm;

import java.util.EnumSet;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;

import com.google.common.collect.ImmutableMap;

import alien4cloud.paas.model.DeploymentStatus;

/**
 * Finite State Machine
 * This class consists of the transition graph and a builder
 */
@WithStateMachine
public class FSM {

	public FSM() {}

	// State transition graph
	private static final ImmutableMap<DeploymentStatus, ImmutableMap<DeploymentStatus, DeploymentMessages>> graph = ImmutableMap.of(
			DeploymentStatus.UNDEPLOYED, ImmutableMap.of(DeploymentStatus.INIT_DEPLOYMENT, DeploymentMessages.DEPLOYMENT_STARTED),
			DeploymentStatus.INIT_DEPLOYMENT, ImmutableMap.of(DeploymentStatus.DEPLOYMENT_IN_PROGRESS, DeploymentMessages.DEPLOYMENT_IN_PROGRESS, DeploymentStatus.FAILURE, DeploymentMessages.FAILURE),
			DeploymentStatus.DEPLOYMENT_IN_PROGRESS, ImmutableMap.of(DeploymentStatus.DEPLOYED, DeploymentMessages.DEPLOYMENT_SUCCESS, DeploymentStatus.FAILURE, DeploymentMessages.FAILURE),
			DeploymentStatus.DEPLOYED, ImmutableMap.of(DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS, DeploymentMessages.UNDEPLOYMENT_STARTED),
			DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS, ImmutableMap.of(DeploymentStatus.UNDEPLOYED, DeploymentMessages.UNDEPLOYMENT_SUCCESS)
	);

	/**
	 * Build a new state machine
	 * @return New state machine
	 */
	protected static StateMachine<DeploymentStatus, DeploymentMessages> buildMachine() {
		StateMachineBuilder.Builder<DeploymentStatus, DeploymentMessages> builder = StateMachineBuilder.builder();

		try {
			builder.configureStates()
					.withStates()
					.initial(DeploymentStatus.UNDEPLOYED)
					.states(EnumSet.allOf(DeploymentStatus.class));

			for (DeploymentStatus start : graph.keySet()) {
				ImmutableMap<DeploymentStatus, DeploymentMessages> target = graph.get(start);
				for (DeploymentStatus end : target.keySet()) {
					DeploymentMessages message = target.get(end);
					builder.configureTransitions()
							.withExternal()
							.source(start).target(end)
							.event(message)
							.and();
				}
			}
		} catch (Exception e) {
			// TODO
			e.printStackTrace();
		}
		return builder.build();
	}

}
