package alien4cloud.paas.yorc.context.service.fsm;

import java.util.EnumSet;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;

import com.google.common.collect.ImmutableMap;

import alien4cloud.paas.model.DeploymentStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Finite State Machine
 * This class consists of the transition graph and a builder
 */
@WithStateMachine
@Slf4j
public class FSM {

	/*
	 States transition graph
	 {source -> target -> input event}
	  */
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
			configureStates(builder, DeploymentStatus.UNDEPLOYED);
			configureTransitions(builder);
			configureConfiguration(builder);
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Error occurred when creating the state machine: " + e.getMessage());
			}
		}
		return builder.build();
	}

	private static void configureConfiguration(StateMachineBuilder.Builder<DeploymentStatus, DeploymentMessages> builder)
			throws Exception {
		builder.configureConfiguration().withConfiguration().autoStartup(true);
	}

	private static void configureTransitions(StateMachineBuilder.Builder<DeploymentStatus, DeploymentMessages> builder)
			throws Exception {
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
	}

	private static void configureStates(StateMachineBuilder.Builder<DeploymentStatus, DeploymentMessages> builder, DeploymentStatus initialState)
			throws Exception {
		builder.configureStates()
				.withStates()
				.initial(initialState)
				.states(EnumSet.allOf(DeploymentStatus.class));
	}

}
