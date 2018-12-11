package alien4cloud.paas.yorc.context.service.fsm;

import java.util.EnumSet;

import javax.inject.Inject;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.stereotype.Service;

/**
 * Finite State Machine builder
 * This class consists of the transition graph and a builder
 */
@Service
public class FsmBuilder {

	@Inject
	private FsmActions actions;

	protected StateMachine<FsmStates, FsmEvent.DeploymentMessages> createFsm(String id, FsmStates initialState) throws Exception {
		StateMachineBuilder.Builder<FsmStates, FsmEvent.DeploymentMessages> builder = StateMachineBuilder.builder();
		configure(builder.configureStates(), initialState);
		configure(builder.configureTransitions());
		configure(builder.configureConfiguration(), id);
		return builder.build();
	}

	private void configure(StateMachineStateConfigurer<FsmStates, FsmEvent.DeploymentMessages> states,
			FsmStates initialState) throws Exception {
		states.withStates()
			.initial(initialState)
			.states(EnumSet.allOf(FsmStates.class));
	}

	private void configure(StateMachineTransitionConfigurer<FsmStates, FsmEvent.DeploymentMessages> transitions)
			throws Exception {
		transitions
			.withExternal()
			.source(FsmStates.UNDEPLOYED).target(FsmStates.DEPLOYMENT_INIT)
			.event(FsmEvent.DeploymentMessages.DEPLOYMENT_STARTED)
			.action(actions.buildAndSendZip())
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_INIT).target(FsmStates.DEPLOYMENT_SUBMITTED)
			.event(FsmEvent.DeploymentMessages.DEPLOYMENT_SUBMITTED)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_INIT).target(FsmStates.FAILED)
			.event(FsmEvent.DeploymentMessages.FAILURE)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_SUBMITTED).target(FsmStates.DEPLOYMENT_IN_PROGRESS)
			.event(FsmEvent.DeploymentMessages.DEPLOYMENT_IN_PROGRESS)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_SUBMITTED).target(FsmStates.FAILED)
			.event(FsmEvent.DeploymentMessages.FAILURE)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.DEPLOYED)
			.event(FsmEvent.DeploymentMessages.DEPLOYMENT_SUCCESS)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvent.DeploymentMessages.UNDEPLOYMENT_STARTED)
			.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_IN_PROGRESS).target(FsmStates.UNDEPLOYED)
			.event(FsmEvent.DeploymentMessages.UNDEPLOYMENT_SUCCESS);
	}

	private void configure(StateMachineConfigurationConfigurer<FsmStates, FsmEvent.DeploymentMessages> config, String id) throws Exception {
		config.withConfiguration().machineId(id).autoStartup(true);
	}
}
