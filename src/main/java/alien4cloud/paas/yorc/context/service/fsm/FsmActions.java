package alien4cloud.paas.yorc.context.service.fsm;

import java.io.IOException;
import java.util.Date;

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;
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
				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
				sendHttpErrorToAlienLogs(context, "Error while sending zip to Yorc", t.getMessage());
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
			String deploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_ID);
			String taskId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.TASK_URL);
			if (log.isInfoEnabled()) {
				log.info(String.format("Cancelling the task %s for deployment %s", taskId, deploymentId));
			}
			deploymentClient.cancelTask(taskId);
		};
	}

	protected Action<FsmStates, FsmEvents> cleanup() {
		return stateContext -> {
			PaaSDeploymentContext context = (PaaSDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);

			// Send event
			stateMachineService.sendEventToAlien(context.getDeploymentPaaSId(), FsmStates.UNDEPLOYED);

			// Cleanup YorcId <-> AlienID
			registry.unregister(context);

			// Cleanup instance infos
			instanceInformationService.remove(context.getDeploymentPaaSId());

			// Remove the FSM
			stateMachineService.deleteStateMachine(context.getDeploymentPaaSId());
		};
	}

	protected Action<FsmStates, FsmEvents> undeploy() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(String value) {
				if (log.isDebugEnabled())
					log.debug("HTTP Request OK : {}", value);
			}

			private void onHttpKo(Throwable t) {
				sendHttpErrorToAlienLogs(context, "Error while sending undeploy order to Yorc", t.getMessage());
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());

				// If 404 received, it means that the deployment has been already undeployed
				if (t instanceof HttpClientErrorException && ((HttpClientErrorException) t).getStatusCode().equals(HttpStatus.NOT_FOUND)) {
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_NOT_EXISTING, context);
					busService.publish(message);
					return;
				}

				// Otherwise, continue the undeploy process
				callback.onFailure(t);
				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
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
				if (log.isDebugEnabled())
					log.debug("HTTP Request OK : {}", value);
				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_PURGED, context);
				busService.publish(message);
			}

			private void onHttpKo(Throwable t) {
				if (callback != null) {
					callback.onFailure(t);
				}
				sendHttpErrorToAlienLogs(context, "Error while sending purge order to Yorc", t.getMessage());

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, context);
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

	private void sendHttpErrorToAlienLogs(PaaSDeploymentContext context, String message, String error) {
		PaaSDeploymentLog logEvent = new PaaSDeploymentLog();
		logEvent.setDeploymentId(context.getDeploymentId());
		logEvent.setDeploymentPaaSId(context.getDeploymentPaaSId());
		logEvent.setLevel(PaaSDeploymentLogLevel.ERROR);
		logEvent.setTimestamp(new Date());
		logEvent.setContent(message + " : " + error);
		logEventService.save(logEvent);
	}

}
