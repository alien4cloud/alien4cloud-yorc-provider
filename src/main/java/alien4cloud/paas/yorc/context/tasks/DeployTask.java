package alien4cloud.paas.yorc.context.tasks;

import alien4cloud.paas.IPaaSCallback;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.service.DeploymentInfo;
import alien4cloud.paas.yorc.context.service.DeploymentService;
import alien4cloud.paas.yorc.service.ZipBuilder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import java.io.IOException;

@Slf4j
@Component
@Scope("prototype")
public class DeployTask extends AbstractTask {

    @Inject
    private DeploymentService deploymentService;

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

        try {
            bytes = zipBuilder.build(info.getContext());
        } catch(IOException e) {
            info.setStatus(DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }

        ListenableFuture<ResponseEntity<String>> f = deploymentClient.sendTopologyToYorc(info.getContext().getDeploymentPaaSId(),bytes);
        f.addCallback(this::onDeploymentSucces,this::onDeployementFailure);
    }

    private void onDeploymentSucces(ResponseEntity<String> value) {
        log.info("Deployment Request ok",value);
    }

    private void onDeployementFailure(Throwable t) {
        log.error("Deployment Failure: {}",t);
    }
}
