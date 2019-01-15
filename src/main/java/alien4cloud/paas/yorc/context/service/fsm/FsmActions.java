package alien4cloud.paas.yorc.context.service.fsm;

import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;

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

	/**
	 * A mapping from deploymentId to taskId
	 */
	private class TaskURLCache {
		private Map<String, String> taskURLCache = Maps.newHashMap();

		private void addURL(String deploymentId, String url) {
			taskURLCache.put(deploymentId, url);
		}

		private String getURL(String deploymentId) {
			String result = taskURLCache.get(deploymentId);
			// Especially in the case of restart, the url does not hit the cache,
			// send a request to fetch the task url
			if (result == null) {
				result = deploymentClient.getTaskURL(deploymentId).blockingGet();
			}
			return result;
		}
	}

	private TaskURLCache urlCache = new TaskURLCache();

	protected Action<FsmStates, FsmEvents> buildAndSendZip() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSTopologyDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(ResponseEntity<String> value) {
				if (log.isInfoEnabled())
					log.info("HTTP Request OK : {}", value);
				String taskURL = value.getHeaders().get("Location").get(0);
				urlCache.addURL(context.getDeploymentPaaSId(), taskURL);
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
			String taskId = urlCache.getURL(deploymentId);
			if (taskId == null && log.isErrorEnabled()) {
				// Normally, this will not happen.
				// Because the task id being null means the deployment is either in success or fail,
				// and in this case, the state of deployment should be deployed or failure.
				log.error(String.format("Cannot cancel a task with null id for the deployment %s", deploymentId));
				return;
			}
			deploymentClient.cancalTask(taskId);
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
