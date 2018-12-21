package alien4cloud.paas.yorc.context.service.fsm;

import javax.inject.Inject;

import alien4cloud.paas.yorc.context.service.InstanceInformationService;
import alien4cloud.paas.yorc.context.service.LogEventService;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.yorc.context.service.BusService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Component
@Slf4j
public class StateMachineService {

	@Inject
	private FsmBuilder builder;


	// TODO Problem of concurrency
	private Map<String, StateMachine<FsmStates, FsmEvents>> cache = Maps.newHashMap();

	private static final Map<FsmStates, DeploymentStatus> statesMapping = ImmutableMap.<FsmStates, DeploymentStatus>builder()
			.put(FsmStates.UNDEPLOYED, DeploymentStatus.UNDEPLOYED)
			.put(FsmStates.DEPLOYMENT_INIT, DeploymentStatus.INIT_DEPLOYMENT)
			.put(FsmStates.DEPLOYMENT_IN_PROGRESS, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.DEPLOYED, DeploymentStatus.DEPLOYED)
			.put(FsmStates.UNDEPLOYMENT_IN_PROGRESS, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.FAILED, DeploymentStatus.FAILURE)
			.build();

	@Inject
	private BusService busService;

	@Inject
	private InstanceInformationService instanceInformationService;

	@Inject
	private LogEventService logEventService;

	/**
	 * Create new state machines with initial state
	 * @param input A map containing deployment id and initial state
	 */
	public void newStateMachine(Map<String,FsmStates> input) {
		for (Map.Entry<String, FsmStates> entry : input.entrySet()) {
			String id = entry.getKey();
			FsmStates initialState = entry.getValue();
			// Create a new state machine
			cache.put(id, createFsm(id, initialState));
			// Create a new event bus to this deployment
			busService.createEventBuses(id);

			// Subscribe the state machine to event bus of message type "deployment"
			busService.subscribe(id,this::talk);

			// Subscribe events on the InstanceInformationService
			busService.subscribeEvents(id,instanceInformationService::onEvent);

			// Subscribe logs events on the LogEventService
			busService.subscribeLogs(id,logEventService::onEvent);
		}
	}

	/**
	 * Create new state machines (default initial state: Undeployed)
	 * @param ids ids of deployments
	 */
	public void newStateMachine(String ...ids) {
		for (String id : ids) {
			// Create a new state machine
			cache.put(id, createFsm(id, FsmStates.UNDEPLOYED));
			// Create a new event bus to this deployment
			busService.createEventBuses(id);
			// Subscribe the state machine to event bus of message type "deployment"
			busService.subscribe(id, this::talk);

            // Subscribe events on the InstanceInformationService
            busService.subscribeEvents(id,instanceInformationService::onEvent);

			// Subscribe logs events on the LogEventService
			busService.subscribeLogs(id,logEventService::onEvent);
		}
	}

	private StateMachine<FsmStates, FsmEvents> createFsm(String id, FsmStates initialState) {
		StateMachine<FsmStates, FsmEvents> fsm = null;
		try {
			fsm = builder.createFsm(id, initialState);
			fsm.addStateListener(new FsmListener(id));
			if (log.isInfoEnabled())
				log.info(String.format("State machine '%s' is created.", id));
		} catch (Exception e) {
			if (log.isInfoEnabled())
				log.info(String.format("Error when creating fsm-%s: %s", id, e.getMessage()));
		}
		return fsm;
	}

	private void talk(Message<FsmEvents> message) {
		String deploymentId = (String) message.getHeaders().get("deploymentId");
		StateMachine<FsmStates, FsmEvents> fsm = cache.get(deploymentId);
		if (fsm != null) {
			fsm.sendEvent(message);
		} else {
			if (log.isErrorEnabled()) {
				log.error(String.format("No state machine found for deployment %s", deploymentId));
			}
		}
	}

	/**
	 * Get the a4c state of given deployment
	 * @param deploymentId Id of deployment
	 * @return a4c corresponding state of this deployment
	 */
	public DeploymentStatus getState(String deploymentId) {
		if (!cache.containsKey(deploymentId)) {
			throw new RuntimeException(String.format("The state machine of %s does not exist.", deploymentId));
		}
		return getState(cache.get(deploymentId).getState().getId());
	}

	private DeploymentStatus getState(FsmStates state) {
		if (statesMapping.containsKey(state)) {
			return statesMapping.get(state);
		}
		if (log.isErrorEnabled()) {
			log.error(String.format("State '%s' is not define in state machine.", state));
		}
		return DeploymentStatus.UNKNOWN;
	}

}
