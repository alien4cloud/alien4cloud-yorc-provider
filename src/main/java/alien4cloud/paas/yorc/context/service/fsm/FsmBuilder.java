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

	protected StateMachine<FsmStates, FsmEvents> createFsm(String id, FsmStates initialState) throws Exception {
		StateMachineBuilder.Builder<FsmStates, FsmEvents> builder = StateMachineBuilder.builder();
		configure(builder.configureStates(), initialState);
		configure(builder.configureTransitions());
		configure(builder.configureConfiguration(), id);
		return builder.build();
	}

	private void configure(StateMachineStateConfigurer<FsmStates, FsmEvents> states,
			FsmStates initialState) throws Exception {
		states.withStates()
			.initial(initialState)
			.states(EnumSet.allOf(FsmStates.class));
	}

	private void configure(StateMachineTransitionConfigurer<FsmStates, FsmEvents> transitions)
			throws Exception {
		transitions
			.withExternal()
			.source(FsmStates.UNDEPLOYED).target(FsmStates.DEPLOYMENT_INIT)
			.event(FsmEvents.DEPLOYMENT_STARTED)
			.action(actions.buildAndSendZip())
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_INIT).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.DEPLOYMENT_CONFLICT)
			.action(actions.cleanup())
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_INIT).target(FsmStates.FAILED)
			.event(FsmEvents.FAILURE)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_INIT).target(FsmStates.DEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.DEPLOYMENT_IN_PROGRESS)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.DEPLOYED)
			.event(FsmEvents.DEPLOYMENT_SUCCESS)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.FAILED)
			.event(FsmEvents.FAILURE)
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.undeploy())
			.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.TASK_CANCELING)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.cancelTask())
			.and()
			.withExternal()
			.source(FsmStates.TASK_CANCELING).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.FAILURE)
			.action(actions.undeploy())
			.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_IN_PROGRESS).target(FsmStates.UNDEPLOYMENT_PURGING)
			.event(FsmEvents.UNDEPLOYMENT_SUCCESS)
			.action(actions.purge())
			.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_IN_PROGRESS).target(FsmStates.FAILED)
			.event(FsmEvents.FAILURE)
			.and()
			.withExternal()
			.source(FsmStates.FAILED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.undeploy())
			.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_IN_PROGRESS).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.DEPLOYMENT_NOT_EXISTING)
			.action(actions.cleanup())
			.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_PURGING).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.DEPLOYMENT_PURGED)
			.action(actions.cleanup());
	}

	private void configure(StateMachineConfigurationConfigurer<FsmStates, FsmEvents> config, String id) throws Exception {
		config.withConfiguration().machineId(id).autoStartup(true);
	}
}
