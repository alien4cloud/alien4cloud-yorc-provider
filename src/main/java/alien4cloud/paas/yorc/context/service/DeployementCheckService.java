package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.response.DeploymentDTO;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvents;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;
import com.google.common.collect.Sets;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DeployementCheckService {

    @Inject
    private Scheduler scheduler;

    @Resource
    private ProviderConfiguration configuration;

    @Inject
    private DeploymentClient client;

    @Inject
    private StateMachineService fsmService;

    @Inject
    private BusService busService;

    private AtomicBoolean running = new AtomicBoolean(true);

    private Disposable disposable;

    public void init() {
        reschedule();
    }

    public void term() {
        running.set(false);

        if (disposable != null) {
            disposable.dispose();
        }
    }

    public void checkTask() {
        if (running.get() == false) {
            return;
        }

        client.get().toMap(DeploymentDTO::getId).subscribe(this::onSuccess,this::onError);
    }

    private void onSuccess(Map<String,DeploymentDTO> deployements) {

        Set<String> alienIds = fsmService.getDeploymentIds();
        Set<String> yorcIds = deployements.keySet();

        // Find deployments for which a FSM exist but unknown to yorc
        Set<String> toRemove = Sets.difference(alienIds,yorcIds);

        // Lets clean
        cleanup(toRemove);

        reschedule();
    }

    private void onError(Throwable t) {
        // An error occur during the request
        log.error("Deployment checker task failed:",t);

        reschedule();
    }

    private void reschedule() {
        // Reschedule eviction task
        disposable = Completable.timer(configuration.getCleanupDeploymentsPeriod(), TimeUnit.SECONDS, scheduler).subscribe(this::checkTask);
    }

    private void cleanup(Set<String> deploymentIds) {
        for (String deploymentId : deploymentIds) {
            // Do not remove in INIT_DEPLOYEMENT because the deployment may not exist in yorc in this state
            if (fsmService.getState(deploymentId) != DeploymentStatus.INIT_DEPLOYMENT) {
                log.info("Deployement {} no longer known from yorc, marking it as undeployed",deploymentId);
                busService.publish(fsmService.createMessage(FsmEvents.EVICTION,deploymentId));
            }
        }
    }
}
