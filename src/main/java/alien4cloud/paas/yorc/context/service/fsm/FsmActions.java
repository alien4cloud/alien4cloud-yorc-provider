package alien4cloud.paas.yorc.context.service.fsm;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.service.BusService;
import alien4cloud.paas.yorc.context.service.DeploymentRegistry;
import alien4cloud.paas.yorc.context.service.InstanceInformationService;
import alien4cloud.paas.yorc.context.service.LogEventService;
import alien4cloud.paas.yorc.service.ZipBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Date;

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
	private StateMachineService stateMachineService;

	@Inject
	private DeploymentRegistry registry;

	@Inject
	private InstanceInformationService instanceInformationService;

	@Inject
	private LogEventService logEventService;

	protected Action<FsmStates, FsmEvents> buildAndSendZip() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSTopologyDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(ResponseEntity<String> value) throws Exception {
				if (log.isDebugEnabled())
					log.debug("HTTP Request OK : {}", value);
				String taskURL = value.getHeaders().get("Location").get(0);
				stateMachineService.setTaskUrl(context.getDeploymentPaaSId(), taskURL);
			}

			private void onHttpKo(Throwable t) {
				callback.onFailure(t);

				// If 409 received, it means that the deployment has already existed in orchestrator
				if (t instanceof HttpClientErrorException && ((HttpClientErrorException) t).getStatusCode().equals(HttpStatus.CONFLICT)) {
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_CONFLICT, context);
					busService.publish(message);
					return;
				}

				// send manually an event to alien
				stateMachineService.sendEventToAlien(context.getDeploymentPaaSId(), FsmStates.FAILED);

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
				sendHttpErrorToAlienLogs(context.getDeploymentPaaSId(), "Error while sending zip to Yorc", t.getMessage());
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());
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
			String deploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
			String taskId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.TASK_URL);
			if (log.isInfoEnabled()) {
				log.info(String.format("Cancelling the task %s for deployment %s", taskId, deploymentId));
			}
			deploymentClient.cancelTask(taskId);
		};
	}

	protected Action<FsmStates, FsmEvents> cleanup() {
		return stateContext -> {
			String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);

			// Send event
			stateMachineService.sendEventToAlien(yorcDeploymentId, FsmStates.UNDEPLOYED);

			// Cleanup YorcId <-> AlienID
			registry.unregister(yorcDeploymentId);

			// Cleanup instance infos
			instanceInformationService.remove(yorcDeploymentId);

			// Remove the FSM
			stateMachineService.deleteStateMachine(yorcDeploymentId);
		};
	}

	protected Action<FsmStates, FsmEvents> undeploy() {
		return new Action<FsmStates, FsmEvents>() {

			private IPaaSCallback<?> callback;
			private String yorcDeploymentId;

			private void onHttpOk(String value) {
				if (log.isDebugEnabled())
					log.debug("HTTP Request OK : {}", value);
			}

			private void onHttpKo(Throwable t) {
				sendHttpErrorToAlienLogs(yorcDeploymentId, "Error while sending undeploy order to Yorc", t.getMessage());
				callback.onFailure(t);

				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());

				// If 404 received, it means that the deployment has been already undeployed
				if (t instanceof HttpClientErrorException && ((HttpClientErrorException) t).getStatusCode().equals(HttpStatus.NOT_FOUND)) {
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_NOT_EXISTING, yorcDeploymentId);
					busService.publish(message);
					return;
				}

				// Otherwise, continue the undeploy process
				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, yorcDeploymentId);
				busService.publish(message);
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				yorcDeploymentId =  (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Undeploying " + yorcDeploymentId);

				deploymentClient.undeploy(yorcDeploymentId).subscribe(this::onHttpOk, this::onHttpKo);
			}
		};
	}

	protected Action<FsmStates, FsmEvents> purge() {
		return new Action<FsmStates, FsmEvents>() {
			private String yorcDeploymentId;
			private IPaaSCallback<?> callback;

			private void onHttpOk(String value) {
				if (log.isDebugEnabled())
					log.debug("HTTP Request OK : {}", value);
				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_PURGED, yorcDeploymentId);
				busService.publish(message);
			}

			private void onHttpKo(Throwable t) {
				if (callback != null) {
					callback.onFailure(t);
				}
				sendHttpErrorToAlienLogs(yorcDeploymentId, "Error while sending purge order to Yorc", t.getMessage());

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, yorcDeploymentId);
				busService.publish(message);
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Purging " + yorcDeploymentId);

				// Cancel subscription
				busService.deleteEventBuses(yorcDeploymentId);

				deploymentClient.purge(yorcDeploymentId).subscribe(this::onHttpOk, this::onHttpKo);
			}

		};
	}

	private void sendHttpErrorToAlienLogs(String yorcDeploymentId, String message, String error) {
		PaaSDeploymentLog logEvent = new PaaSDeploymentLog();
		logEvent.setDeploymentId(registry.toAlienId(yorcDeploymentId));
		logEvent.setDeploymentPaaSId(yorcDeploymentId);
		logEvent.setLevel(PaaSDeploymentLogLevel.ERROR);
		logEvent.setTimestamp(new Date());
		logEvent.setContent(message + " : " + error);
		logEventService.save(logEvent);
	}

}
