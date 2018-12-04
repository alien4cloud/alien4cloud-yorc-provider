package alien4cloud.paas.yorc.context.service.fsm;

import java.util.EnumSet;

import javax.inject.Inject;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
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

	@Inject
	private ApplicationContext context;

	/**
	 * Build a new state machine
	 * @return New state machine
	 */
	@Bean(name = "buildMachine")
	@Scope("prototype")
	@Lazy(false)
	protected StateMachine<FsmStates, DeploymentMessages> buildMachine(FsmStates initialStates) {
		StateMachineBuilder.Builder<FsmStates, DeploymentMessages> builder = StateMachineBuilder.builder();
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

	private void configureConfiguration(StateMachineBuilder.Builder<FsmStates, DeploymentMessages> builder)
			throws Exception {
		builder.configureConfiguration().withConfiguration().autoStartup(true);
	}

	/**
	 * Transition graph
	 * @param builder state machine builder
	 * @throws Exception exception
	 */
	private void configureTransitions(StateMachineBuilder.Builder<FsmStates, DeploymentMessages> builder)
			throws Exception {
		builder.configureTransitions()
				.withExternal()
				.source(FsmStates.UNDEPLOYED).target(FsmStates.DEPLOYMENT_INIT)
				.event(DeploymentMessages.DEPLOYMENT_STARTED)
				.action((Action<FsmStates, DeploymentMessages>) context.getBean("buildAndSendZip"))
				.and()
				.withExternal()
				.source(FsmStates.DEPLOYMENT_INIT).target(FsmStates.DEPLOYMENT_SUBMITTED)
				.event(DeploymentMessages.DEPLOYMENT_SUBMITTED)
				.and()
				.withExternal()
				.source(FsmStates.DEPLOYMENT_SUBMITTED).target(FsmStates.DEPLOYMENT_IN_PROGRESS)
				.event(DeploymentMessages.DEPLOYMENT_IN_PROGRESS)
				.and()
				.withExternal()
				.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.DEPLOYED)
				.event(DeploymentMessages.DEPLOYMENT_SUCCESS)
				.and()
				.withExternal()
				.source(FsmStates.DEPLOYED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
				.event(DeploymentMessages.UNDEPLOYMENT_STARTED)
				.and()
				.withExternal()
				.source(FsmStates.UNDEPLOYMENT_IN_PROGRESS).target(FsmStates.UNDEPLOYED)
				.event(DeploymentMessages.UNDEPLOYMENT_SUCCESS);

	}

	private void configureStates(StateMachineBuilder.Builder<FsmStates, DeploymentMessages> builder, FsmStates initialState)
			throws Exception {
		builder.configureStates()
				.withStates()
				.initial(initialState)
				.states(EnumSet.allOf(FsmStates.class));
	}

}
