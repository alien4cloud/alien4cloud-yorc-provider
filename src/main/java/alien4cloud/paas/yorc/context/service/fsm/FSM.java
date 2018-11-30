package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Finite State Machine
 * This class consists of the transition graph and a builder
 */
@WithStateMachine
@Slf4j
public class FSM {

	/**
	 * States transition graph
	 */
	private static final List<TransitionElement> graph = Arrays.asList(
			new TransitionElement(DeploymentStates.UNDEPLOYED, DeploymentStates.DEPLOYMENT_INIT, DeploymentMessages.DEPLOYMENT_STARTED, DeploymentActions.buildZip()),
			new TransitionElement(DeploymentStates.DEPLOYMENT_INIT, DeploymentStates.DEPLOYMENT_SUBMITTED, DeploymentMessages.DEPLOYMENT_SUBMITTED, null),
			new TransitionElement(DeploymentStates.DEPLOYMENT_SUBMITTED, DeploymentStates.DEPLOYMENT_IN_PROGRESS, DeploymentMessages.DEPLOYMENT_IN_PROGRESS, null),
			new TransitionElement(DeploymentStates.DEPLOYMENT_IN_PROGRESS, DeploymentStates.DEPLOYED, DeploymentMessages.DEPLOYMENT_SUCCESS, null),
			new TransitionElement(DeploymentStates.DEPLOYED, DeploymentStates.UNDEPLOYMENT_IN_PROGRESS, DeploymentMessages.UNDEPLOYMENT_STARTED, null),
			new TransitionElement(DeploymentStates.UNDEPLOYMENT_IN_PROGRESS, DeploymentStates.UNDEPLOYED, DeploymentMessages.UNDEPLOYMENT_SUCCESS, null)
			);

	/**
	 * Build a new state machine
	 * @return New state machine
	 */
	protected static StateMachine<DeploymentStates, DeploymentMessages> buildMachine(DeploymentStates initialStates) {
		StateMachineBuilder.Builder<DeploymentStates, DeploymentMessages> builder = StateMachineBuilder.builder();
		try {
			configureStates(builder, initialStates);
			configureTransitions(builder);
			configureConfiguration(builder);
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Error occurred when creating the state machine: " + e.getMessage());
			}
		}
		return builder.build();
	}

	private static void configureConfiguration(StateMachineBuilder.Builder<DeploymentStates, DeploymentMessages> builder)
			throws Exception {
		builder.configureConfiguration().withConfiguration().autoStartup(true);
	}

	private static void configureTransitions(StateMachineBuilder.Builder<DeploymentStates, DeploymentMessages> builder)
			throws Exception {
		for (TransitionElement element : graph) {
			builder.configureTransitions()
					.withExternal()
					.source(element.getSource()).target(element.getTarget())
					.event(element.getInputEvent())
					.action(element.getAction())
					.and();
		}
	}

	private static void configureStates(StateMachineBuilder.Builder<DeploymentStates, DeploymentMessages> builder, DeploymentStates initialState)
			throws Exception {
		builder.configureStates()
				.withStates()
				.initial(initialState)
				.states(EnumSet.allOf(DeploymentStates.class));
	}

}
