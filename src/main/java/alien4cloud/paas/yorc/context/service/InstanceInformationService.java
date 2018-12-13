package alien4cloud.paas.yorc.context.service;

import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.browser.Browser;
import alien4cloud.paas.yorc.context.rest.response.AttributeDTO;
import alien4cloud.paas.yorc.context.rest.response.DeploymentDTO;
import alien4cloud.paas.yorc.context.rest.response.InstanceDTO;
import alien4cloud.paas.yorc.context.rest.response.NodeDTO;
import com.google.common.collect.Maps;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
public class InstanceInformationService {

    @Inject
    private DeploymentClient client;

    @Inject
    private Scheduler scheduler;

    private static class DeploymentInformation {

        // Stream used for lazy initialization
        Observable<Browser.Context> stream;

        // Lazy init in progress
        private Disposable disposable;

        // Lock used for the init
        private final Lock lockInit = new ReentrantLock();

        // Lock for the database
        private final ReadWriteLock lockInfo = new ReentrantReadWriteLock();

        // Our InstanceInformations
        private final Map<String,Map<String,InstanceInformation>> informations = Maps.newConcurrentMap();
    }

    private final Map<String,DeploymentInformation> map = Maps.newConcurrentMap();

    /**
     * Initialization
     *
     * @param deployementIds knowns deploymentIds
     */
    public void init(Set<String> deployementIds) {
        for (String deplomentId : deployementIds) {
            DeploymentInformation information = new DeploymentInformation();

            // Prepare the query
            // - Note that the query is deferred until subscription time
            Observable<String> links = Observable.just("/deployments/" + deplomentId);
            information.stream = Browser.browserFor(links, url -> client.queryUrl(url,DeploymentDTO.class),1)
                .flatMap( agg -> agg.follow("node", url -> client.queryUrl(url,NodeDTO.class),2))
                .flatMap( agg -> agg.follow("instance", url -> client.queryUrl(url,InstanceDTO.class),2))
                .flatMap( agg -> agg.follow("attribute", url -> client.queryUrl(url,AttributeDTO.class),2));

            map.put(deplomentId,information);
        }
    }

    public void getInformation(String deploymentPaaSId, IPaaSCallback<Map<String,Map<String,InstanceInformation>>> callback) {
        DeploymentInformation di = map.get(deploymentPaaSId);
        if (di == null) {
            callback.onSuccess(new HashMap<>());
            return;
        }

        if (di.stream != null) {
            Completable complete = null;

            // A Lazy init should be done
            try {
                di.lockInit.lock();
                if (di.stream != null) {
                    complete = Completable.fromObservable(di.stream);
                    if (di.disposable == null) {
                        // The query is not running, start it
                        di.disposable = di.stream.observeOn(scheduler).subscribe(this::onLazyInit);
                    }
                }
            } finally {
                di.lockInit.unlock();
            }

            // Then wait for its completion
            complete.blockingAwait();
        }

        try {
            di.lockInfo.readLock().lock();
            callback.onSuccess(di.informations);
        } finally {
            di.lockInfo.readLock().unlock();
        }
    }

    private void onLazyInit(Browser.Context context) {
        DeploymentDTO deploymentDTO = (DeploymentDTO) context.get(0);
        NodeDTO nodeDTO = (NodeDTO) context.get(1);
        InstanceDTO instanceDTO = (InstanceDTO) context.get(2);
        AttributeDTO attributeDTO = (AttributeDTO) context.get(3);

        DeploymentInformation di = update(deploymentDTO.getId(),nodeDTO.getName(),instanceDTO,attributeDTO);

        // Lazy init has been done
        try {
            di.lockInit.lock();
            di.stream = null;
        } finally {
            di.lockInit.unlock();
        }
    }

    private DeploymentInformation update(String deploymentId,String nodeId,InstanceDTO instanceDTO,AttributeDTO attributeDTO) {
        DeploymentInformation di = map.get(deploymentId);
        if (di == null) {
            di = new DeploymentInformation();
            map.put(deploymentId,di);
        }

        try {
            di.lockInfo.writeLock().lock();

            Map<String,InstanceInformation> ni = di.informations.get(nodeId);
            if (ni == null) {
                ni = Maps.newHashMap();
                di.informations.put(nodeId,ni);
            }

            InstanceInformation ii = ni.get(instanceDTO.getId());
            if (ii == null) {
                ii = new InstanceInformation(
                        ToscaNodeLifecycleConstants.INITIAL,
                        getInstanceStatusFromState(instanceDTO.getStatus()),
                        Maps.newHashMap(),
                        Maps.newHashMap(),
                        Maps.newHashMap()
                    );
                ni.put(instanceDTO.getId(),ii);
            }

            if (ii.getAttributes().get(attributeDTO.getName()) == null) {
                ii.getAttributes().put(attributeDTO.getName(),attributeDTO.getValue());
            }
        } finally {
            di.lockInfo.writeLock().unlock();
        }

        log.info("YORC ATTR {}/{}/{} {}={}",deploymentId,nodeId,instanceDTO.getId(),attributeDTO.getName(),attributeDTO.getValue());

        return di;
    }

    /**
     * return Instance Status from the instance state
     * See yorc/tosca/states.go (_NodeState_name) but other states may exist for custom commands
     * @param state
     * @return
     */
    private static InstanceStatus getInstanceStatusFromState(String state) {
        switch (state) {
            case "started":
            case "published":
            case "finished":
            case "done":
                return InstanceStatus.SUCCESS;
            case "deleted":
                return null;
            case "error":
                return InstanceStatus.FAILURE;
            default:
                return InstanceStatus.PROCESSING;
        }
    }
}
