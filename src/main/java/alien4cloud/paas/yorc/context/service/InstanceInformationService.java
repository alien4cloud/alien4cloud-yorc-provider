package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.browser.Browser;
import alien4cloud.paas.yorc.context.rest.response.*;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InstanceInformationService {

    @Inject
    private DeploymentClient client;

    @Inject
    private Scheduler scheduler;

    private static class DeploymentInformation {

        // Atomic Reference to our stream
        private final AtomicReference<Observable<Browser.Context>> stream;

        private final CountDownLatch completed;

        // Lock for the database
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        // Our InstanceInformations
        private final Map<String,Map<String,InstanceInformation>> informations = Maps.newHashMap();

        private DeploymentInformation() {
            this.stream = null;
            this.completed = new CountDownLatch(0);
        }

        private DeploymentInformation(Observable<Browser.Context> stream) {
            this.completed = new CountDownLatch(1);
            this.stream = new AtomicReference<>(
                    stream
                        .doOnComplete(() -> this.completed.countDown())
                        .doOnError( x -> this.completed.countDown())
            );
        }
    }

    private final Map<String,DeploymentInformation> map = Maps.newConcurrentMap();

    /**
     * Initialization
     *
     * @param deployementIds knowns deploymentIds
     */
    public void init(Set<String> deployementIds) {
        deployementIds = Sets.newLinkedHashSet(deployementIds);

        for (String deploymentId : deployementIds) {

            // Prepare the query
            // - Note that the query is deferred until subscription time
            Observable<String> links = Observable.just("/deployments/" + deploymentId);

            DeploymentInformation di = new DeploymentInformation(
                Browser.browserFor(links, url -> client.queryUrl(url,DeploymentDTO.class),1)
                    .flatMap( agg -> agg.follow("node", url -> client.queryUrl(url,NodeDTO.class),5))
                    .flatMap( agg -> agg.follow("instance", url -> client.queryUrl(url,InstanceDTO.class), 5))
                    .flatMap( agg -> agg.follow("attribute", url -> client.queryUrl(url,AttributeDTO.class),10))
                    .doOnSubscribe( x -> log.info("INST/ATTR Queries started for {}",deploymentId))
                    .doOnError( x -> log.error("INST/ATTR Queries KO for {} : {}",deploymentId,x.getMessage()))
                    .doOnComplete( () -> log.info("INST/ATTR Queries OK for {}",deploymentId))
                );

            map.put(deploymentId,di);
        }

        // Lazy initialization
        Observable.fromIterable(deployementIds).concatMapDelayError(this::initializeStreamFor).subscribe(this::onAttribute,this::onError);
    }

    public void getInformation(String deploymentPaaSId, IPaaSCallback<Map<String,Map<String,InstanceInformation>>> callback) {
        DeploymentInformation di = map.get(deploymentPaaSId);
        if (di == null) {
            // Deployment unknown => nothing to provide
            callback.onSuccess(new HashMap<>());
            return;
        }

        // Check wether there is a init stream
        Observable<Browser.Context> stream = initializeStreamFor(di);
        if (stream != null) {
            // We got a init stream, we must run it
            stream.observeOn(scheduler).subscribe(this::onAttribute,this::onError);
        }

        // Wait for latch
        try {
            di.completed.await();
        } catch(InterruptedException e) {
            callback.onFailure(e);
        }

        try {
            di.lock.readLock().lock();
            callback.onSuccess(di.informations);
        } finally {
            di.lock.readLock().unlock();
        }
    }

    private Observable<Browser.Context> initializeStreamFor(DeploymentInformation di) {
        if (di == null || di.stream == null) {
            return null;
        }

        return di.stream.getAndSet(null);
    }

    private Observable<Browser.Context> initializeStreamFor(String deploymentId) {
        DeploymentInformation di = map.get(deploymentId);

        Observable<Browser.Context> result = initializeStreamFor(di);
        if (result == null) {
            // This is null because some one launch the subscription before
            return Observable.empty();
        } else {
            return result;
        }
    }

    private void onError(Throwable t) {
        log.error("YORC exception while querying instance/attribute: {}",t.getMessage());
    }

    private void onAttribute(Browser.Context context) {
        DeploymentDTO deploymentDTO = (DeploymentDTO) context.get(0);
        NodeDTO nodeDTO = (NodeDTO) context.get(1);
        InstanceDTO instanceDTO = (InstanceDTO) context.get(2);
        AttributeDTO attributeDTO = (AttributeDTO) context.get(3);

        DeploymentInformation di = updateAttribute(deploymentDTO.getId(),nodeDTO.getName(),instanceDTO,attributeDTO);
    }

    private DeploymentInformation updateAttribute(String deploymentId, String nodeId, InstanceDTO instanceDTO, AttributeDTO attributeDTO) {
        DeploymentInformation di = map.computeIfAbsent(deploymentId,(k) -> new DeploymentInformation());

        try {
            di.lock.writeLock().lock();

            // Update the instance
            InstanceInformation ii = updateInstance(di,nodeId,instanceDTO.getId(),instanceDTO.getStatus());

            // Update the attributes
            if (ii != null) {
                ii.getAttributes().putIfAbsent(attributeDTO.getName(), attributeDTO.getValue());
            }
        } finally {
            di.lock.writeLock().unlock();
        }

        //log.debug("YORC ATTR {}/{}/{} {}={}",deploymentId,nodeId,instanceDTO.getId(),attributeDTO.getName(),attributeDTO.getValue());

        return di;
    }

    private InstanceInformation updateInstance(DeploymentInformation di,String nodeId,String instanceId,String status) {
        InstanceInformation ii = null;
        Map<String,InstanceInformation> ni = di.informations.computeIfAbsent(nodeId,(key) -> Maps.newHashMap());

        if (status.equals("deleted")) {
           ni.remove(instanceId);
        } else {
            ii = ni.computeIfAbsent(instanceId, (key) -> buildInstance());

            ii.setState(status);
            ii.setInstanceStatus(getInstanceStatusFromState(status));
        }

        return ii;
    }

    public void onEvent(Event event) {
        if (event.getType().equals("instance")) {
            DeploymentInformation di = map.computeIfAbsent(event.getDeployment_id(),(k) -> new DeploymentInformation());

            if (di != null) {
                updateInstance(di,event.getNode(),event.getInstance(),event.getStatus());
            }

            log.info("YORC INST {}/{}/{}->{}",event.getDeployment_id(),event.getNode(),event.getInstance(),event.getStatus());
        }
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

    private static InstanceInformation buildInstance() {
        return new InstanceInformation(
            ToscaNodeLifecycleConstants.INITIAL,
            InstanceStatus.PROCESSING,
            Maps.newHashMap(),
            Maps.newHashMap(),
            Maps.newHashMap()
        );
    }
}
