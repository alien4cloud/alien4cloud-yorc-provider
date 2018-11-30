package alien4cloud.paas.yorc.context.tasks;

import java.io.IOException;

import javax.inject.Inject;

import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.service.DeploymentInfo;
import alien4cloud.paas.yorc.context.service.DeploymentService;
import alien4cloud.paas.yorc.context.service.EventService;
import alien4cloud.paas.yorc.context.service.fsm.DeploymentEvent;
import alien4cloud.paas.yorc.context.service.fsm.DeploymentMessages;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;
import alien4cloud.paas.yorc.service.ZipBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Scope("prototype")
public class DeployTask extends AbstractTask {

    @Inject
    private DeploymentService deploymentService;

    @Inject
    private StateMachineService fsmService;

    @Inject
    DeploymentClient deploymentClient;

    @Inject
    private ZipBuilder zipBuilder;

    @Inject
    private EventService eventService;

    @Getter
    private DeploymentInfo info;

    private IPaaSCallback<?> callback;

    /**
     * Start the deploy task.
     *
     * Called from UI Thread
     *
     * @param context
     * @param callback
     */
    public void start(PaaSTopologyDeploymentContext context, IPaaSCallback<?> callback) {
        this.info = deploymentService.createDeployment(context);

        this.callback = callback;

        //getExecutorService().submit(this::doStart);
        updateStatus(DeploymentMessages.DEPLOYMENT_STARTED);
    }

    /**
     * Starting the deployment on task pool
     */
    public void doStart() {
        byte[] bytes;

        if (log.isDebugEnabled())
            log.debug("Deploying " + info.getContext().getDeploymentPaaSId() + " with id : " + info.getContext()
                    .getDeploymentId());

        try {
            bytes = zipBuilder.build(info.getContext());
        } catch(IOException e) {
            updateStatus(DeploymentMessages.FAILURE);
            callback.onFailure(e);
            return;
        }

        ListenableFuture<ResponseEntity<String>> f = deploymentClient.sendTopologyToYorc(info.getContext().getDeploymentPaaSId(),bytes);
        f.addCallback(this::onDeploymentRequestSuccess,this::onDeploymentRequestFailure);

        updateStatus(DeploymentMessages.DEPLOYMENT_SUBMITTED);
    }

    private void onDeploymentRequestSuccess(ResponseEntity<String> value) {
        updateStatus(DeploymentMessages.DEPLOYMENT_SUCCESS);
        if (log.isInfoEnabled()) {
            log.info("Deployment Request ok",value);
        }

        eventService.subscribe(info.getContext().getDeploymentPaaSId(),this::onEvent);
    }

    private void onDeploymentRequestFailure(Throwable t) {
        updateStatus(DeploymentMessages.FAILURE);
        if (log.isErrorEnabled())
            log.error("Deployment Failure: {}", t);
    }

    private void onEvent(Event event) {
        if (log.isInfoEnabled())
            log.info("Event: {}", event);
    }

    private void updateStatus(DeploymentMessages message) {
        info.setStatus(fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), message, this)));
    }
}
