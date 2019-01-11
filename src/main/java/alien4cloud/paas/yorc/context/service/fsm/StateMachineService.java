package alien4cloud.paas.yorc.context.service.fsm;

import java.util.Map;

import javax.inject.Inject;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.yorc.context.service.BusService;
import alien4cloud.paas.yorc.context.service.InstanceInformationService;
import alien4cloud.paas.yorc.context.service.LogEventService;
import alien4cloud.paas.yorc.context.service.WorkflowInformationService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class StateMachineService {

	@Inject
	private FsmBuilder builder;

	public static final String DEPLOYMENT_CONTEXT = "deploymentContext";
	public static final String DEPLOYMENT_ID = "deploymentId";
	public static final String CALLBACK = "callback";


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
	private WorkflowInformationService workflowInformationService;

	@Inject
	private LogEventService logEventService;

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

			doSubscriptions(id);
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

			doSubscriptions(id);
		}
	}

	private void doSubscriptions(String id ) {
		// Create a new event bus to this deployment
		busService.createEventBuses(id);

		// Subscribe the state machine to event bus of message type "deployment"
		busService.subscribe(id, this::talk);

		// Subscribe events on the InstanceInformationService
		busService.subscribeEvents(id,instanceInformationService::onEvent);

		// Subscribe events on the WorkflowInformationService
		busService.subscribeEvents(id,workflowInformationService::onEvent);

		// Subscribe logs events on the LogEventService
		busService.subscribeLogs(id,logEventService::onEvent);
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
		String deploymentId = (String) message.getHeaders().get(DEPLOYMENT_ID);
		StateMachine<FsmStates, FsmEvents> fsm = cache.get(deploymentId);
		if (fsm != null) {
			// Set up some necessary variables for each fsm
			PaaSDeploymentContext context = (PaaSDeploymentContext) message.getHeaders().get(DEPLOYMENT_CONTEXT);
			IPaaSCallback<?> callback = (IPaaSCallback<?>) message.getHeaders().get(CALLBACK);
			if (context != null)
				fsm.getExtendedState().getVariables().put(DEPLOYMENT_CONTEXT, context);
			if (callback != null)
				fsm.getExtendedState().getVariables().put(CALLBACK, callback);
			fsm.getExtendedState().getVariables().put(DEPLOYMENT_ID, deploymentId);

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

	/**
	 * Create a fsm message with deployment id and context
	 * @param event FsmEvents
	 * @param deploymentContext Deployment context
	 * @return New created message
	 */
	public Message<FsmEvents> createMessage(FsmEvents event, PaaSDeploymentContext deploymentContext) {
		return MessageBuilder.withPayload(event)
				.setHeader(StateMachineService.DEPLOYMENT_CONTEXT, deploymentContext)
				.setHeader(StateMachineService.DEPLOYMENT_ID, deploymentContext.getDeploymentPaaSId())
				.build();
	}

	/**
	 * Create a fsm message with deployment id and context as well as alien callback
	 * @param event FsmEvents
	 * @param deploymentContext Deployment context
	 * @param callback Alien callback
	 * @return New created message
	 */
	public Message<FsmEvents> createMessage(FsmEvents event, PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
		return MessageBuilder.withPayload(event)
				.setHeader(StateMachineService.CALLBACK, callback)
				.setHeader(StateMachineService.DEPLOYMENT_CONTEXT, deploymentContext)
				.setHeader(StateMachineService.DEPLOYMENT_ID, deploymentContext.getDeploymentPaaSId())
				.build();
	}

}
