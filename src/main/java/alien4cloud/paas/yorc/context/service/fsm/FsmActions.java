package alien4cloud.paas.yorc.context.service.fsm;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.response.DeploymentDTO;
import alien4cloud.paas.yorc.context.rest.response.Error;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.Link;
import alien4cloud.paas.yorc.context.rest.response.PurgeDTO;
import alien4cloud.paas.yorc.context.service.BusService;
import alien4cloud.paas.yorc.context.service.DeploymentRegistry;
import alien4cloud.paas.yorc.context.service.InstanceInformationService;
import alien4cloud.paas.yorc.context.service.LogEventService;
import alien4cloud.paas.yorc.service.ZipBuilder;
import alien4cloud.paas.yorc.util.RestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import io.jsonwebtoken.lang.Collections;
import io.reactivex.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.normative.constants.NormativeWorkflowNameConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.Iterator;

import static alien4cloud.utils.AlienUtils.safe;

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

	@Resource
	private ProviderConfiguration configuration;

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
				if (t instanceof HttpClientErrorException) {
					String body = ((HttpClientErrorException) t).getResponseBodyAsString();

					try {
						JsonNode node = RestUtil.toJson().apply(body);

						for (Iterator<JsonNode> i = node.path("errors").elements() ; i.hasNext() ; ) {
							JsonNode e = i.next();
							String title = RestUtil.jsonAsText("title").apply(e);
							String detail = RestUtil.jsonAsText("detail").apply(e);

							sendHttpErrorToAlienLogs(context.getDeploymentPaaSId(),title,detail);
						}
					} catch(Exception e) {
						// Cannot extract errors from body, we just log the exception
						sendHttpErrorToAlienLogs(context.getDeploymentPaaSId(), "Error while sending zip to Yorc", t.getMessage());
					}
				}

				// Notify failure
				callback.onFailure(t);

				// If 409 received, it means that the deployment has already existed in orchestrator
				if (t instanceof HttpClientErrorException && ((HttpClientErrorException) t).getStatusCode().equals(HttpStatus.CONFLICT)) {
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_CONFLICT, context);
					busService.publish(message);
					return;
				}

				if (t instanceof HttpServerErrorException && ((HttpServerErrorException) t).getStatusCode().equals(HttpStatus.GATEWAY_TIMEOUT)) {
					log.warn("504 (Gateway Timeout) error while sending deployment to Yorc, ignoring");
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.GATEWAY_TIMEOUT, context);
					busService.publish(message);
					return;
				}

				if (t instanceof SocketTimeoutException) {
					log.warn("Socket timeout while sending deployment to Yorc, ignoring");
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.GATEWAY_TIMEOUT, context);
					busService.publish(message);
					return;
				}

				doFail();
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
					doFail();
					return;
				} catch (RuntimeException e) {
					callback.onFailure(e);
					doFail();
					return;
				}

				deploymentClient.sendTopology(context.getDeploymentPaaSId(), bytes, false).subscribe(this::onHttpOk, this::onHttpKo);
			}

			private void doFail() {
				// send manually an event to alien
				stateMachineService.sendEventToAlien(context.getDeploymentPaaSId(), FsmStates.FAILED);

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
			}
		};
	}

	protected Action<FsmStates, FsmEvents> requestCancelTask() {
	    return new Action<FsmStates, FsmEvents>() {

			private PaaSTopologyDeploymentContext context;

			private void onHttpOk(DeploymentDTO deployment) {
				if (log.isDebugEnabled()) {
					log.debug("{} links found for deployment {}", Collections.size(deployment.getLinks()), deployment.getId());
				}
				for (Link link : deployment.getLinks()) {
					if (link.getRel().equals("task")) {
						stateMachineService.setTaskUrl(deployment.getId(),link.getHref());

						Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.UNDEPLOYMENT_STARTED, deployment.getId());
						busService.publish(message);

						break;
					}
				}
			}

			private void onHttpKo(Throwable t) {
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				String taskUrl = stateMachineService.getTaskUrl(yorcDeploymentId);

				if (taskUrl == null) {
					if (log.isDebugEnabled()) {
						log.debug("Retrieving deployment {} to find some running tasks", yorcDeploymentId);
					}
					deploymentClient.get(yorcDeploymentId).subscribe(this::onHttpOk, this::onHttpKo);
				} else {
					if (log.isDebugEnabled()) {
						log.debug("A task url has be found for deployment {}: {}", yorcDeploymentId, taskUrl);
					}
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.UNDEPLOYMENT_STARTED, yorcDeploymentId);
					busService.publish(message);
				}
			}
		};
    }

	protected Action<FsmStates, FsmEvents> cancelTask() {
		return stateContext -> {
			String deploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
			String taskId = stateMachineService.getTaskUrl(deploymentId);

			if (log.isInfoEnabled()) {
				log.info(String.format("Cancelling the task %s for deployment %s", taskId, deploymentId));
			}
			deploymentClient.cancelTask(taskId);
		};
	}

	protected Action<FsmStates, FsmEvents> forceRefreshAttributes() {
		return stateContext -> {
			String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);

			instanceInformationService.forceRefresh(yorcDeploymentId);
		};
	}

	protected Action<FsmStates, FsmEvents> cleanup() {
		return stateContext -> {
			String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);

			// Send event
			stateMachineService.sendEventToAlien(yorcDeploymentId, FsmStates.UNDEPLOYED);

			doCleanup(yorcDeploymentId);
		};
	}

	protected Action<FsmStates, FsmEvents> cleanupNoEvt() {
		return stateContext -> {
			String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);

			doCleanup(yorcDeploymentId);
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
				boolean stopOnError = configuration.getUndeployStopOnError();
				boolean force = false;

				if (stateContext.getMessageHeaders().containsKey(StateMachineService.FORCE)) {
					force = (boolean) stateContext.getMessageHeader(StateMachineService.FORCE);
				}

				yorcDeploymentId =  (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Undeploying " + yorcDeploymentId);

				deploymentClient.undeploy(yorcDeploymentId,stopOnError && !force).subscribe(this::onHttpOk, this::onHttpKo);
			}
		};
	}

	protected Action<FsmStates, FsmEvents> preUpdate() {
		return new Action<FsmStates, FsmEvents>() {
			private String yorcDeploymentId;
			private IPaaSCallback<?> callback;
			private PaaSTopologyDeploymentContext context;

			private void onHttpOk(String value) {
				if (log.isDebugEnabled()) {
					log.debug("Workflow pre_update launched for deployment {} : {}", yorcDeploymentId, value);
				}
				// store the taskUrl for eventually undeploy during update
				stateMachineService.setTaskUrl(context.getDeploymentPaaSId(), value);

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.PRE_UPDATE_STARTED, yorcDeploymentId);
				busService.publish(message);
			}

			private void onHttpKo(Throwable t) {
				if (log.isDebugEnabled()) {
					log.debug("Workflow pre_update launched for deployment {} throws errors", yorcDeploymentId, t);
				}

				if (t instanceof HttpClientErrorException && ((HttpClientErrorException)t).getStatusCode() == HttpStatus.NOT_FOUND) {
					if (log.isWarnEnabled()) {
						log.warn("Workflow pre_update launched for deployment {} throws a 404 errors : the initial topology doesn't have a pre_update workflow but the new one has, just ignore and continue the update", yorcDeploymentId, t);
					}
					// this can occur when the new updated topology has a pre_update workflow but not the old one
					// in the case, just ignore the exception
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.PRE_UPDATE_SKIPPED, yorcDeploymentId);
					busService.publish(message);
					return;
				}

				if (callback != null) {
					callback.onFailure(t);
				}

				// we need to publish a deployment event
				Event event = new Event();
				event.setType(Event.EVT_DEPLOYMENT);
				event.setDeploymentId(yorcDeploymentId);
				event.setStatus("UPDATE_FAILURE");
				busService.publish(event);

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.PRE_UPDATE_FAILURE, yorcDeploymentId);
				busService.publish(message);
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);
				if (log.isDebugEnabled()) {
					log.debug("Executing preUpdate action for deployment {}", yorcDeploymentId);
				}
				context = (PaaSTopologyDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);
				if (!context.getDeploymentTopology().getWorkflows().containsKey(NormativeWorkflowNameConstants.PRE_UPDATE)) {
					if (log.isDebugEnabled()) {
						log.info("no pre_update workflow for deployment {}", yorcDeploymentId);
					}
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.PRE_UPDATE_SKIPPED, yorcDeploymentId);
					busService.publish(message);
					return;
				}
				// we need to publish a deployment event
				Event event = new Event();
				event.setType(Event.EVT_DEPLOYMENT);
				event.setDeploymentId(yorcDeploymentId);
				event.setStatus("UPDATE_IN_PROGRESS");
				busService.publish(event);

				if (log.isInfoEnabled()) {
					log.info("Launching pre_update workflow for deployment {}", yorcDeploymentId);
				}
				deploymentClient.executeWorkflow(yorcDeploymentId, NormativeWorkflowNameConstants.PRE_UPDATE, Maps.newHashMap(), false).subscribe(this::onHttpOk, this::onHttpKo);
			}

		};
	}

	protected Action<FsmStates, FsmEvents> update() {
		return new Action<FsmStates, FsmEvents>() {

			private IPaaSCallback<?> callback;
			private PaaSTopologyDeploymentContext context;

			private void onHttpOk(ResponseEntity<String> value) throws Exception {
				if (log.isDebugEnabled())
					log.debug("HTTP Request OK : {}", value);

				// Update can generate task in some cases (add/remove nodes)
				if (value.getHeaders().get("Location") != null) {
					String taskURL = value.getHeaders().get("Location").get(0);
					stateMachineService.setTaskUrl(context.getDeploymentPaaSId(), taskURL);
				}
			}

			private void onHttpKo(Throwable t) {
				if (t instanceof HttpClientErrorException) {
					String body = ((HttpClientErrorException) t).getResponseBodyAsString();

					try {
						JsonNode node = RestUtil.toJson().apply(body);

						for (Iterator<JsonNode> i = node.path("errors").elements() ; i.hasNext() ; ) {
							JsonNode e = i.next();
							String title = RestUtil.jsonAsText("title").apply(e);
							String detail = RestUtil.jsonAsText("detail").apply(e);

							sendHttpErrorToAlienLogs(context.getDeploymentPaaSId(),title,detail);
						}
					} catch(Exception e) {
						// Cannot extract errors from body, we just log the exception
						sendHttpErrorToAlienLogs(context.getDeploymentPaaSId(), "Error while sending zip to Yorc for updating topology", t.getMessage());
					}
				}

				// Notify failure
				callback.onFailure(t);

				// If 409 received, it means that the dupdate is conflicting with another task
				if (t instanceof HttpClientErrorException && ((HttpClientErrorException) t).getStatusCode().equals(HttpStatus.CONFLICT)) {
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_CONFLICT, context);
					busService.publish(message);
					return;
				}

				// send manually an event to alien
				stateMachineService.sendEventToAlien(context.getDeploymentPaaSId(), FsmStates.UPDATE_FAILED);

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.FAILURE, context);
				busService.publish(message);
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				byte[] bytes;
				context = (PaaSTopologyDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Updating " + context.getDeploymentPaaSId() + " with id : " + context.getDeploymentId());

				try {
					bytes = zipBuilder.build(context);
				} catch (IOException e) {
					callback.onFailure(e);
					return;
				}

				deploymentClient.sendTopology(context.getDeploymentPaaSId(), bytes, true).subscribe(this::onHttpOk, this::onHttpKo);
			}
		};
	}

	protected Action<FsmStates, FsmEvents> postUpdate() {
		return new Action<FsmStates, FsmEvents>() {
			private String yorcDeploymentId;
			private IPaaSCallback<?> callback;
			private PaaSTopologyDeploymentContext context;

			private void onHttpOk(String value) {
				if (log.isDebugEnabled()) {
					log.debug("Workflow post_update launched for deployment {} : {}", yorcDeploymentId, value);
				}
				// store the taskUrl for eventually undeploy during update
				stateMachineService.setTaskUrl(context.getDeploymentPaaSId(), value);
			}

			private void onHttpKo(Throwable t) {
				if (callback != null) {
					callback.onFailure(t);
				}

				// we need to publish a deployment event
				Event event = new Event();
				event.setType(Event.EVT_DEPLOYMENT);
				event.setDeploymentId(yorcDeploymentId);
				event.setStatus("UPDATE_FAILURE");
				busService.publish(event);

				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.POST_UPDATE_FAILURE, yorcDeploymentId);
				busService.publish(message);
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);
				if (log.isDebugEnabled()) {
					log.debug("Executing postUpdate action for deployment {}", yorcDeploymentId);
				}
				context = (PaaSTopologyDeploymentContext) stateContext.getExtendedState().getVariables().get(StateMachineService.DEPLOYMENT_CONTEXT);
				if (!context.getDeploymentTopology().getWorkflows().containsKey(NormativeWorkflowNameConstants.POST_UPDATE)) {
					if (log.isDebugEnabled()) {
						log.info("no post_update workflow for deployment {}", yorcDeploymentId);
					}
					Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.POST_UPDATE_SUCCESS, yorcDeploymentId);
					busService.publish(message);
					return;
				}
                // we need to publish a deployment event
                Event event = new Event();
                event.setType(Event.EVT_DEPLOYMENT);
                event.setDeploymentId(yorcDeploymentId);
                event.setStatus("UPDATE_IN_PROGRESS");
				busService.publish(event);

				if (log.isInfoEnabled()) {
					log.info("Launching post_update workflow for deployment {}", yorcDeploymentId);
				}
				deploymentClient.executeWorkflow(yorcDeploymentId, NormativeWorkflowNameConstants.POST_UPDATE, Maps.newHashMap(),false).subscribe(this::onHttpOk, this::onHttpKo);
			}

		};
	}

	protected Action<FsmStates, FsmEvents> notifyUpdateSucces() {
		return stateContext -> {

			IPaaSCallback<?> callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);
			if (callback != null) {
				if (log.isDebugEnabled()) {
					log.debug("Calling a4c update callback");
				}
				callback.onSuccess(null);
			}

			String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);

			// we need to publish a deployment event
			Event event = new Event();
			event.setType(Event.EVT_DEPLOYMENT);
			event.setDeploymentId(yorcDeploymentId);
			event.setStatus("UPDATED");
			busService.publish(event);

		};
	}

	protected Action<FsmStates, FsmEvents> notifyPrePostUpdateFailure() {
		return stateContext -> {

			String yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);

			// we need to publish a deployment event
			Event event = new Event();
			event.setType(Event.EVT_DEPLOYMENT);
			event.setDeploymentId(yorcDeploymentId);
			event.setStatus("UPDATE_FAILURE");
			busService.publish(event);

			IPaaSCallback<?> callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);
			if (callback != null) {
				if (log.isDebugEnabled()) {
					log.debug("Calling a4c update callback to notify error");
				}
				callback.onFailure(new Exception("Postupdate workflow failed, see logs ..."));
			}
		};
	}

	protected Action<FsmStates, FsmEvents> syncPurge() {
		final Function<String, PurgeDTO> mapper = RestUtil.mapperFor(PurgeDTO.class);

		return new Action<FsmStates, FsmEvents>() {
			private String yorcDeploymentId;
			private IPaaSCallback<?> callback;

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				yorcDeploymentId = (String) stateContext.getExtendedState().getVariables().get(StateMachineService.YORC_DEPLOYMENT_ID);
				callback = (IPaaSCallback<?>) stateContext.getExtendedState().getVariables().get(StateMachineService.CALLBACK);

				if (log.isInfoEnabled())
					log.info("Purging(v2) " + yorcDeploymentId);

				deploymentClient.syncPurge(yorcDeploymentId, false).subscribe(this::onHttpOk, this::onHttpKo);
			}

			private void onHttpOk(PurgeDTO dto) {
				log.info("Environment {} purged",yorcDeploymentId);
				Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_PURGED, yorcDeploymentId);
				busService.publish(message);
			}

			private void onHttpKo(Throwable t) {
				if (t instanceof HttpServerErrorException) {
					HttpServerErrorException httpError = (HttpServerErrorException) t;

					log.error("Purge for deployment {} returned error {}", yorcDeploymentId, httpError.getStatusCode());

					try {
						PurgeDTO dto = mapper.apply(httpError.getResponseBodyAsString());

						for (Error error : safe(dto.getErrors())) {
							log.error("Purge Error deployment=<{}> code=<{}> title=<{}> detail=<{}> :",
									yorcDeploymentId,
									error.getStatus(),
									error.getTitle(),
									error.getDetail()
							);

							sendHttpErrorToAlienLogs(yorcDeploymentId,error.getTitle(),error.getDetail());
						}

						callback.onFailure(t);
					} catch (Exception e) {
						log.error("Cannot parse purge error detail", e);
					}
				} else {
					log.error("Unexpected purge exception: {}", t);
				}
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

				deploymentClient.purge(yorcDeploymentId,configuration.getUndeployStopOnError()).subscribe(this::onHttpOk, this::onHttpKo);
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

	private void doCleanup(String yorcDeploymentId) {
		// Cancel subscriptions
		busService.unsubscribeEvents(yorcDeploymentId);

		// Delete Events Buses
		busService.deleteEventBuses(yorcDeploymentId);

		// Cleanup YorcId <-> AlienID
		registry.unregister(yorcDeploymentId);

		// Cleanup instance infos
		instanceInformationService.remove(yorcDeploymentId);

		// Remove the FSM
		stateMachineService.deleteStateMachine(yorcDeploymentId);
	}
}
