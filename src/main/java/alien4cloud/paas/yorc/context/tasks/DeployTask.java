package alien4cloud.paas.yorc.context.tasks;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import alien4cloud.paas.yorc.context.rest.response.Event;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.service.DeploymentInfo;
import alien4cloud.paas.yorc.context.service.DeploymentService;
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

    private EventListener listener;

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
        //fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.DEPLOYMENT_STARTED));

        try {
            bytes = zipBuilder.build(info.getContext());
        } catch(IOException e) {
            //fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.FAILURE));
            //info.setStatus(DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }

        // We start the subscription now because we can receive events before the completion of the http request
        listener = EventListener.builder()
            .when(Event.EVT_DEPLOYMENT,"deployment_failed",this::onEventFailed)
            .when(Event.EVT_DEPLOYMENT, "deployed",this::onEventDeployed)
            .when(Event.EVT_DEPLOYMENT, "deployment_in_progress",this::onEventInProgess)
            .withTimeout(10,TimeUnit.SECONDS,this::onTimeout)
            .build(info.getEvents());

        listener.subscribe();

        // Sent our zip
        ListenableFuture<ResponseEntity<String>> f = deploymentClient.sendTopologyToYorc(info.getContext().getDeploymentPaaSId(),bytes);
        f.addCallback(this::onHttpOk,this::onHttpKo);

        //fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.DEPLOYMENT_IN_PROGRESS));
    }

    private void onHttpOk(ResponseEntity<String> value) {
        //fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.DEPLOYMENT_SUCCESS));
        log.info("HTTP Request OK : {}", value);
    }

    private void onHttpKo(Throwable t) {
        //fsmService.sendEvent(new DeploymentEvent(info.getContext().getDeploymentId(), DeploymentMessages.FAILURE));
        log.error("HTTP Request OK : {}", t);
        listener.cancel();
    }

    private void onEventFailed(Event event) {
        log.info("EVENT:Failed");
    }

    private void onEventDeployed(Event event) {
        log.info("EVENT:Deployed");
        listener.cancel();
    }

    private void onEventInProgess(Event event) {
        log.info("EVENT:InProgress");
    }

    private void onTimeout(Throwable t) {
        log.info("TimeOut");
        listener.cancel();
    }

}
