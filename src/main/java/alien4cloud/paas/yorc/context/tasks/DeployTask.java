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
import alien4cloud.paas.yorc.context.service.DeploymentInfo;
import alien4cloud.paas.yorc.context.service.DeploymentService;
import alien4cloud.paas.yorc.context.service.fsm.DeploymentEvent;
import alien4cloud.paas.yorc.context.service.fsm.DeploymentMessages;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;
import alien4cloud.paas.yorc.service.ZipBuilder;
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

        getExecutorService().submit(this::doStart);
    }

    /**
     * Starting the deployment on task pool
     */
    private void doStart() {
        byte[] bytes;

        log.debug("Deploying " + info.getContext().getDeploymentPaaSId() + " with id : " + info.getContext().getDeploymentId());

        //TODO Not sure is deploymentId or deploymentPaaSId
        //TODO Should it be done by event bus?
        fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.DEPLOYMENT_STARTED));

        try {
            bytes = zipBuilder.build(info.getContext());
        } catch(IOException e) {
            fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.FAILURE));
            //info.setStatus(DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }

        ListenableFuture<ResponseEntity<String>> f = deploymentClient.sendTopologyToYorc(info.getContext().getDeploymentPaaSId(),bytes);
        f.addCallback(this::onDeploymentSuccess,this::onDeploymentFailure);

        fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.DEPLOYMENT_IN_PROGRESS));
    }

    private void onDeploymentSuccess(ResponseEntity<String> value) {
        fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.DEPLOYMENT_SUCCESS));
        log.info("Deployment Request ok",value);
    }

    private void onDeploymentFailure(Throwable t) {
        fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.FAILURE));
        log.error("Deployment Failure: {}",t);
    }
}
