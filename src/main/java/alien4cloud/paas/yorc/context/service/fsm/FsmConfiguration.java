package alien4cloud.paas.yorc.context.service.fsm;

import java.util.EnumSet;

import javax.inject.Inject;

import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

/**
 * Finite State Machine
 * This class consists of the transition graph and a builder
 */
@EnableStateMachineFactory
public class FsmConfiguration extends EnumStateMachineConfigurerAdapter<FsmStates, FsmEvent.DeploymentMessages> {

	private static final FsmStates initialState = FsmStates.UNDEPLOYED;

	@Inject
	private FsmActions actions;

	@Override
	public void configure(StateMachineStateConfigurer<FsmStates, FsmEvent.DeploymentMessages> states) throws Exception {
		states.withStates()
			.initial(initialState)
			.states(EnumSet.allOf(FsmStates.class));
	}

	@Override
	public void configure(StateMachineTransitionConfigurer<FsmStates, FsmEvent.DeploymentMessages> transitions)
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

	@Override
	public void configure(StateMachineConfigurationConfigurer<FsmStates, FsmEvent.DeploymentMessages> config) throws Exception {
		config.withConfiguration().autoStartup(true);
	}
}
