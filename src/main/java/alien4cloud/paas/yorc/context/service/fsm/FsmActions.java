package alien4cloud.paas.yorc.context.service.fsm;

import java.io.IOException;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

import com.google.common.collect.ImmutableMap;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.service.EventBusService;
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
	private EventBusService eventBusService;

	@Bean
	protected Action<FsmStates, FsmEvent.DeploymentMessages> buildAndSendZip() {
		return new Action<FsmStates, FsmEvent.DeploymentMessages>() {

			private PaaSTopologyDeploymentContext context;
			private IPaaSCallback<?> callback;

			private void onHttpOk(ResponseEntity<String> value) {
				eventBusService.publish(new FsmEvent(context.getDeploymentPaaSId(), FsmEvent.DeploymentMessages.DEPLOYMENT_SUBMITTED, ImmutableMap.of()));
				log.info("HTTP Request OK : {}", value);
			}

			private void onHttpKo(Throwable t) {
				eventBusService.publish(new FsmEvent(context.getDeploymentPaaSId(), FsmEvent.DeploymentMessages.FAILURE, ImmutableMap.of()));
				log.error("HTTP Request OK : {}", t.getMessage());
			}

			@Override
			public void execute(StateContext<FsmStates, FsmEvent.DeploymentMessages> stateContext) {
				byte[] bytes;
				context = (PaaSTopologyDeploymentContext) stateContext.getMessageHeaders().get("deploymentContext");
				callback = (IPaaSCallback<?>) stateContext.getMessageHeaders().get("callback");

				log.debug("Deploying " + context.getDeploymentPaaSId() + " with id : " + context.getDeploymentId());

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
}
