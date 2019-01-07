package alien4cloud.paas.yorc.context.service.fsm;

import java.io.IOException;

import javax.inject.Inject;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

import alien4cloud.paas.IPaaSCallback;
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

	protected Action<FsmStates, FsmEvents> buildAndSendZip() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSTopologyDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(ResponseEntity<String> value) {
				if (log.isInfoEnabled())
					log.info("HTTP Request OK : {}", value);
			}

			private void onHttpKo(Throwable t) {
				Message<FsmEvents> message = MessageBuilder.withPayload(FsmEvents.FAILURE)
						.setHeader("deploymentId", context.getDeploymentPaaSId())
						.build();

				busService.publish(message);
				if (log.isErrorEnabled())
					log.error("HTTP Request OK : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				byte[] bytes;
				context = (PaaSTopologyDeploymentContext) stateContext.getMessageHeaders().get("deploymentContext");
				callback = (IPaaSCallback<?>) stateContext.getMessageHeaders().get("callback");

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

	protected Action<FsmStates, FsmEvents> undeploy() {
		return new Action<FsmStates, FsmEvents>() {

			private PaaSTopologyDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(String value) {
				if (log.isInfoEnabled())
					log.info("HTTP Request OK : {}", value);
				Message<FsmEvents> message = MessageBuilder.withPayload(FsmEvents.UNDEPLOYMENT_SUCCESS)
						.setHeader("deploymentId", context.getDeploymentPaaSId())
						.build();
			}

			private void onHttpKo(Throwable t) {
				callback.onFailure(t);
				Message<FsmEvents> message = MessageBuilder.withPayload(FsmEvents.FAILURE)
						.setHeader("deploymentId", context.getDeploymentPaaSId())
						.build();

				busService.publish(message);
				if (log.isErrorEnabled())
					log.error("HTTP Request KO : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvents> stateContext) {
				context = (PaaSTopologyDeploymentContext) stateContext.getMessageHeaders().get("deploymentContext");
				callback = (IPaaSCallback<?>) stateContext.getMessageHeaders().get("callback");

				if (log.isInfoEnabled())
					log.info("Undeploying " + context.getDeploymentPaaSId() + " with id : " + context.getDeploymentId());

				deploymentClient.undeploy(context.getDeploymentPaaSId()).subscribe(this::onHttpOk, this::onHttpKo);
			}
		};
	}
}
