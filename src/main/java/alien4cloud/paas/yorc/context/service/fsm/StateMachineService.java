package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Map;

import javax.inject.Inject;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.service.EventBusService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class StateMachineService {

	@Inject
	private FsmBuilder builder;


	// TODO Problem of concurrency
	private Map<String, StateMachine<FsmStates, FsmEvent.DeploymentMessages>> cache = Maps.newHashMap();

	private static final Map<FsmStates, DeploymentStatus> statesMapping = ImmutableMap.<FsmStates, DeploymentStatus>builder()
			.put(FsmStates.UNDEPLOYED, DeploymentStatus.UNDEPLOYED)
			.put(FsmStates.DEPLOYMENT_INIT, DeploymentStatus.INIT_DEPLOYMENT)
			.put(FsmStates.DEPLOYMENT_SUBMITTED, DeploymentStatus.INIT_DEPLOYMENT)
			.put(FsmStates.DEPLOYMENT_IN_PROGRESS, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.DEPLOYED, DeploymentStatus.DEPLOYED)
			.put(FsmStates.UNDEPLOYMENT_IN_PROGRESS, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.FAILED, DeploymentStatus.FAILURE)
			.build();

	@Inject
	private EventBusService eventBusService;

	@Inject
	private FsmEventsConsumer consumer;

	/**
	 * Create new state machines with initial state
	 * @param input A map containing deployment id and initial state
	 */
	public void newStateMachine(Map<String, FsmStates> input) {
		for (Map.Entry<String, FsmStates> entry : input.entrySet()) {
			String id = entry.getKey();
			FsmStates initialState = entry.getValue();
			// Create a new state machine
			cache.put(id, createFsm(id, initialState));
			// Create a new event bus to this deployment
			eventBusService.createEventBus(id);
			// Subscribe the state machine to event bus of message type "deployment"
			eventBusService.subscribe(id, Event.EVT_DEPLOYMENT, consumer);
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
			eventBusService.createEventBus(id);
			// Subscribe the state machine to event bus of message type "deployment"
			eventBusService.subscribe(id, Event.EVT_DEPLOYMENT, consumer);
		}
	}

	private StateMachine<FsmStates, FsmEvent.DeploymentMessages> createFsm(String id, FsmStates initialState) {
		StateMachine<FsmStates, FsmEvent.DeploymentMessages> fsm = null;
		try {
			fsm = builder.createFsm(id, initialState);
			fsm.addStateListener(new FsmListener(id));
			log.error(String.format("State machine '%s' is created.", id));
		} catch (Exception e) {
			log.error(String.format("Error when creating fsm-%s: %s", id, e.getMessage()));
		}
		return fsm;
	}

	/**
	 * Send an event to the state machine to decide the next state
	 * @param event Input event
	 * @return Next state
	 */
	public FsmStates talk(FsmEvent event) {
		if (!cache.containsKey(event.getDeployment_id())) {
			throw new RuntimeException(String.format("The state machine of %s does not exist.", event.getDeployment_id()));
		}
		MessageBuilder<FsmEvent.DeploymentMessages> builder = MessageBuilder
				.withPayload(event.getMessage());
		for (String key : event.getHeaders().keySet()) {
			builder.setHeader(key, event.getHeaders().get(key));
		}
		Message<FsmEvent.DeploymentMessages> message = builder.build();
		StateMachine<FsmStates, FsmEvent.DeploymentMessages> fsm = cache.get(event.getDeployment_id());
		fsm.sendEvent(message);
		return cache.get(event.getDeployment_id()).getState().getId();
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
