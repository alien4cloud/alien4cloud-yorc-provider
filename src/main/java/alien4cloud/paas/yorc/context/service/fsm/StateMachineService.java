package alien4cloud.paas.yorc.context.service.fsm;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.service.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.naming.SelfNaming;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
@ManagedResource
public class StateMachineService implements SelfNaming {

	@Resource
	private ProviderConfiguration configuration;

	@Inject
	private FsmBuilder builder;

	@Inject
	private YorcOrchestrator orchestrator;

	@Inject
	private DeploymentRegistry registry;

	public static final String DEPLOYMENT_CONTEXT = "deploymentContext";
	public static final String YORC_DEPLOYMENT_ID = "yorcDeploymentId";
	public static final String CALLBACK = "callback";
	public static final String TASK_URL = "taskURL";

	private Map<String, StateMachine<FsmStates, FsmEvents>> cache = Maps.newConcurrentMap();

	private static final Map<FsmStates, DeploymentStatus> statesMapping = ImmutableMap.<FsmStates, DeploymentStatus>builder()
			.put(FsmStates.UNDEPLOYED, DeploymentStatus.UNDEPLOYED)
			.put(FsmStates.DEPLOYMENT_INIT, DeploymentStatus.INIT_DEPLOYMENT)
			.put(FsmStates.DEPLOYMENT_IN_PROGRESS, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.DEPLOYED, DeploymentStatus.DEPLOYED)
            .put(FsmStates.CANCELLATION_REQUESTED, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.TASK_CANCELLING, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.UNDEPLOYMENT_IN_PROGRESS, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.UNDEPLOYMENT_PURGING, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS)
			.put(FsmStates.UPDATE_IN_PROGRESS, DeploymentStatus.UPDATE_IN_PROGRESS)
			.put(FsmStates.POST_UPDATE_IN_PROGRESS, DeploymentStatus.UPDATE_IN_PROGRESS)
			.put(FsmStates.PRE_UPDATE_IN_PROGRESS, DeploymentStatus.UPDATE_IN_PROGRESS)
			.put(FsmStates.UPDATED, DeploymentStatus.UPDATED)
			.put(FsmStates.UPDATE_FAILED, DeploymentStatus.UPDATE_FAILURE)
			.put(FsmStates.FAILED, DeploymentStatus.FAILURE)
			.put(FsmStates.UNKOWN, DeploymentStatus.DEPLOYMENT_IN_PROGRESS)
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

	public void deleteStateMachine(String id) {
		cache.remove(id);
	}

	private void doSubscriptions(String id) {
		// Create a new event bus to this deployment
		busService.createEventBuses(id);

		// Subscribe the state machine to event bus of message type "deployment"
		busService.subscribe(id, this::talk);

		// Subscribe events on the InstanceInformationService
		busService.subscribeEvents(id,instanceInformationService::onEvent);

		// Subscribe events on the WorkflowInformationService
		busService.subscribeEvents(id,workflowInformationService::onEvent);
	}

	private StateMachine<FsmStates, FsmEvents> createFsm(String id, FsmStates initialState) {
		StateMachine<FsmStates, FsmEvents> fsm = null;
		try {
			fsm = builder.createFsm(id, initialState);
			fsm.addStateListener(new FsmListener(id));
			if (log.isDebugEnabled())
				log.debug(String.format("State machine '%s' is created.", id));
		} catch (Exception e) {
			if (log.isErrorEnabled())
				log.error(String.format("Error when creating fsm-%s: %s", id, e.getMessage()));
		}
		return fsm;
	}

	private void talk(Message<FsmEvents> message) {
		String deploymentId = (String) message.getHeaders().get(YORC_DEPLOYMENT_ID);
		StateMachine<FsmStates, FsmEvents> fsm = cache.get(deploymentId);
		if (fsm != null) {
			// Set up some necessary variables for each fsm
			PaaSDeploymentContext context = (PaaSDeploymentContext) message.getHeaders().get(DEPLOYMENT_CONTEXT);
			IPaaSCallback<?> callback = (IPaaSCallback<?>) message.getHeaders().get(CALLBACK);
			if (context != null)
				fsm.getExtendedState().getVariables().put(DEPLOYMENT_CONTEXT, context);
			if (callback != null)
				fsm.getExtendedState().getVariables().put(CALLBACK, callback);
			fsm.getExtendedState().getVariables().put(YORC_DEPLOYMENT_ID, deploymentId);

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
		StateMachine<FsmStates, FsmEvents> fsm = cache.get(deploymentId);

		if (fsm == null) {
			return DeploymentStatus.UNDEPLOYED;
		} else {
			return getState(fsm.getState().getId());
		}
	}

	public Set<String> getDeploymentIds() {
		return cache.keySet();
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
	 * @param deploymentId Deployment paas id
	 * @return New created message
	 */
	public Message<FsmEvents> createMessage(FsmEvents event, String deploymentId) {
		return MessageBuilder.withPayload(event)
				.setHeader(StateMachineService.YORC_DEPLOYMENT_ID, deploymentId)
				.build();
	}

	/**
	 * Create a fsm message with deployment id and context
	 * @param event FsmEvents
	 * @param deploymentContext Deployment context
	 * @return New created message
	 */
	protected Message<FsmEvents> createMessage(FsmEvents event, PaaSDeploymentContext deploymentContext) {
		return MessageBuilder.withPayload(event)
				.setHeader(StateMachineService.DEPLOYMENT_CONTEXT, deploymentContext)
				.setHeader(StateMachineService.YORC_DEPLOYMENT_ID, deploymentContext.getDeploymentPaaSId())
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
				.setHeader(StateMachineService.YORC_DEPLOYMENT_ID, deploymentContext.getDeploymentPaaSId())
				.build();
	}

	/**
	 * Send event of a4c type to Alien
	 * @param deploymentId Deployment paas id
	 * @param state Fsm state
	 */
	public void sendEventToAlien(String deploymentId, FsmStates state) {
		PaaSDeploymentStatusMonitorEvent event = new PaaSDeploymentStatusMonitorEvent();
		event.setDeploymentStatus(getState(state));
		event.setDeploymentId(registry.toAlienId(deploymentId));
		orchestrator.postAlienEvent(event);
		if (log.isDebugEnabled()) {
			log.debug(String.format("Append event %s to Alien", event));
		}
	}

	public void setTaskUrl(String deploymentId, String url) {
		StateMachine<FsmStates, FsmEvents> fsm = cache.get(deploymentId);

		if (fsm != null) {
			fsm.getExtendedState().getVariables().put(TASK_URL, url);
		} else {
			log.warn("Fsm-{} does not exist", deploymentId);
		}
	}

	public String getTaskUrl(String deploymentId) {
        StateMachine<FsmStates, FsmEvents> fsm = cache.get(deploymentId);
        if (fsm != null) {
            return (String) fsm.getExtendedState().getVariables().get(TASK_URL);
        } else {
            return null;
        }
    }

	/**
	 * Set the deployment id for the according fsm.
	 */
	public void setYorcDeploymentId(String yorcDeploymentId) throws Exception {
		if (!cache.containsKey(yorcDeploymentId)) {
			throw new Exception(String.format("Fsm-%s does not exist", yorcDeploymentId));
		}
		Map<Object, Object> variables = cache.get(yorcDeploymentId).getExtendedState().getVariables();
		variables.put(YORC_DEPLOYMENT_ID, yorcDeploymentId);
	}

	@ManagedAttribute
	public long getStateMachineCount() {
		return cache.size();
	}

	@Override
	public ObjectName getObjectName() throws MalformedObjectNameException {
		Hashtable<String,String> kv = new Hashtable();
		kv.put("type","Orchestrators");
		kv.put("orchestratorName",configuration.getOrchestratorName());
		kv.put("name","StateMachineService");
		return new ObjectName("alien4cloud.paas.yorc",kv);
	}
}
