package alien4cloud.paas.yorc.context.service.fsm;

import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;

import alien4cloud.paas.yorc.context.service.DeployementRegistry;
import alien4cloud.paas.yorc.context.service.InstanceInformationService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.service.BusService;
import alien4cloud.paas.yorc.service.ZipBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FsmActions {

	@Inject
	DeploymentClient deploymentClient;

	@Inject
	private ZipBuilder zipBuilder;

	@Inject
	private BusService busService;

	@Inject
	private StateMachineService fsmService;

	@Inject
	private DeployementRegistry registry;

	@Inject
	private InstanceInformationService instanceInformationService;

	/**
	 * A mapping from deploymentId to taskId
	 */
	//TODO How to handle the cache better? (maybe need to be persisted?)
	private Map<String, String> taskURLCache = Maps.newHashMap();

	protected Action<FsmStates, FsmEvents> buildAndSendZip() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSTopologyDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(ResponseEntity<String> value) {
				if (log.isInfoEnabled())
					log.info("HTTP Request OK : {}", value);
				String taskURL = value.getHeaders().get("Location").get(0);
				taskURLCache.put(context.getDeploymentPaaSId(), taskURL);
			}

			private void onHttpKo(Throwable t) {
				Message<FsmEvents> message = fsmService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
				if (log.isErrorEnabled())
					log.error("HTTP Request OK : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				byte[] bytes;
				context = (PaaSTopologyDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Deploying " + context.getDeploymentPaaSId() + " with id : " + context.getDeploymentId());

				try {
					bytes = zipBuilder.build(context);
				} catch (IOException e) {
					callback.onFailure(e);
					return;
				}

				deploymentClient.sendTopology(context.getDeploymentPaaSId(), bytes).subscribe(this::onHttpOk, this::onHttpKo);
			}
		};
	}

	protected Action<FsmStates, FsmEvents> cancelTask() {
		return stateContext -> {
			String deploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_ID);
			String taskId = taskURLCache.get(deploymentId);
			deploymentClient.cancalTask(taskId);
		};
	}

	protected Action<FsmStates, FsmEvents> cleanup() {
		return new Action<FsmStates, FsmEvents>() {
			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				PaaSDeploymentContext context = (PaaSDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);

				// Cleanup YorcId <-> AlienID
				registry.unregister(context);

				// Cleanup instance infos
				instanceInformationService.remove(context.getDeploymentPaaSId());

				// Remove the FSM
				fsmService.deleteStateMachine(context.getDeploymentPaaSId());
			}
		};
	}

	protected Action<FsmStates, FsmEvents> undeploy() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(String value) {
				if (log.isInfoEnabled())
					log.info("HTTP Request OK : {}", value);
			}

			private void onHttpKo(Throwable t) {
				callback.onFailure(t);
				Message<FsmEvents> message = fsmService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				context = (PaaSDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Undeploying " + context.getDeploymentPaaSId() + " with id : " + context.getDeploymentId());

				deploymentClient.undeploy(context.getDeploymentPaaSId()).subscribe(this::onHttpOk, this::onHttpKo);
			}
		};
	}

	protected Action<FsmStates, FsmEvents> purge() {
		return new Action<FsmStates, FsmEvents>() {
			private PaaSDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(String value) {
				if (log.isInfoEnabled())
					log.info("HTTP Request OK : {}", value);
				Message<FsmEvents> message = fsmService.createMessage(FsmEvents.DEPLOYMENT_PURGED, context);
				busService.publish(message);
			}

			private void onHttpKo(Throwable t) {
				callback.onFailure(t);
				Message<FsmEvents> message = fsmService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				context = (PaaSDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Purging " + context.getDeploymentPaaSId() + " with id : " + context.getDeploymentId());

				deploymentClient.purge(context.getDeploymentPaaSId()).subscribe(this::onHttpOk, this::onHttpKo);
			}

		};
	}
}
