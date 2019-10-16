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
			.action(actions.forceRefreshAttributes())
		.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.FAILED)
			.event(FsmEvents.FAILURE)

// Update stuff here
// Allow update from DEPLOYED state
		.and()
			.withExternal()
			.source(FsmStates.DEPLOYED).target(FsmStates.UPDATE_IN_PROGRESS)
			.event(FsmEvents.UPDATE_STARTED)
			.action(actions.update())
// Allow update from UPDATED state
		.and()
			.withExternal()
			.source(FsmStates.UPDATED).target(FsmStates.UPDATE_IN_PROGRESS)
			.event(FsmEvents.UPDATE_STARTED)
			.action(actions.update())
// Allow update from UPDATE_FAILED state
		.and()
			.withExternal()
			.source(FsmStates.UPDATE_FAILED).target(FsmStates.UPDATE_IN_PROGRESS)
			.event(FsmEvents.UPDATE_STARTED)
			.action(actions.update())
// Success
//		.and()
//			.withExternal()
//			.source(FsmStates.UPDATE_IN_PROGRESS).target(FsmStates.UPDATED)
//			.event(FsmEvents.UPDATE_SUCCESS)
//			.action(actions.notifyUpdateSucces())
		.and()
			.withExternal()
			.source(FsmStates.UPDATE_IN_PROGRESS).target(FsmStates.POST_UPDATE_IN_PROGRESS)
			.event(FsmEvents.UPDATE_SUCCESS)
			.action(actions.launchPostUpdateWorkflow())
		.and()
			.withExternal()
			.source(FsmStates.POST_UPDATE_IN_PROGRESS).target(FsmStates.UPDATED)
			.event(FsmEvents.POST_UPDATE_SUCCESS)
			.action(actions.notifyPostUpdateSucces())
		.and()
			.withExternal()
			.source(FsmStates.POST_UPDATE_IN_PROGRESS).target(FsmStates.UPDATE_FAILED)
			.event(FsmEvents.POST_UPDATE_FAILURE)
			.action(actions.notifyPostUpdateFailure())
		.and()
			.withExternal()
			.source(FsmStates.POST_UPDATE_IN_PROGRESS).target(FsmStates.CANCELLATION_REQUESTED)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.requestCancelTask())
		.and()
			.withExternal()
			.source(FsmStates.TASK_CANCELLING).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.POST_UPDATE_CANCELED)
			.action(actions.undeploy())
// Failure
		.and()
			.withExternal()
			.source(FsmStates.UPDATE_IN_PROGRESS).target(FsmStates.UPDATE_FAILED)
			.event(FsmEvents.FAILURE)
// Allow undeploy from UPDATED state
		.and()
			.withExternal()
			.source(FsmStates.UPDATED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.undeploy())
// Allow undeploy from UPDATE_FAILED state
		.and()
			.withExternal()
			.source(FsmStates.UPDATE_FAILED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.undeploy())

		.and()
			.withExternal()
			.source(FsmStates.DEPLOYED).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.undeploy())
		.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.CANCELLATION_REQUESTED)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.requestCancelTask())
		.and()
			.withExternal()
			.source(FsmStates.CANCELLATION_REQUESTED).target(FsmStates.TASK_CANCELLING)
			.event(FsmEvents.UNDEPLOYMENT_STARTED)
			.action(actions.cancelTask())
		.and()
			.withExternal()
			.source(FsmStates.TASK_CANCELLING).target(FsmStates.UNDEPLOYMENT_IN_PROGRESS)
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
			.action(actions.cleanupNoEvt())
		.and()
			.withExternal()
			.source(FsmStates.UPDATE_FAILED).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.UPDATED).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.UPDATE_IN_PROGRESS).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.DEPLOYMENT_IN_PROGRESS).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.DEPLOYED).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.TASK_CANCELLING).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_IN_PROGRESS).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.UNDEPLOYMENT_PURGING).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
		.and()
			.withExternal()
			.source(FsmStates.FAILED).target(FsmStates.UNDEPLOYED)
			.event(FsmEvents.EVICTION)
			.action(actions.cleanup())
			;
	}

	private void configure(StateMachineConfigurationConfigurer<FsmStates, FsmEvents> config, String id) throws Exception {
		config.withConfiguration().machineId(id).autoStartup(true);
	}
}
