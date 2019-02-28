package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.utils.jackson.MapEntry;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class DeploymentRegistry {

    @Inject
    private Scheduler scheduler;

    @Inject
    ProviderConfiguration configuration;

    // Eviction Task is running
    private AtomicBoolean running = new AtomicBoolean(true);

    // Eviction Task Disposable
    private Disposable disposable;

    private static class DeploymentData {

        static DeploymentData defVal = new DeploymentData();

        private DeploymentData() {
        }

        private DeploymentData(String yorcId,String alienId) {
            this.alienId = alienId;
            this.yorcId = yorcId;
        }

        private String yorcId;
        private String alienId;
        private Instant deleted;
    }

    /**
     * Map AlienID -> DeploymentData
     */
    private final Map<String,DeploymentData> aMap = Maps.newHashMap();

    /**
     * Map YorcID -> DeploymentData
     */
    private final Map<String,DeploymentData> yMap = Maps.newHashMap();

    public synchronized void register(String yDeploymentId, String aDeploymentId) {
            DeploymentData data = new DeploymentData(yDeploymentId,aDeploymentId);

            yMap.remove(yDeploymentId);

            aMap.put(aDeploymentId,data);
            yMap.put(yDeploymentId,data);
    }

    public synchronized void unregister(String yDeploymentId) {
        DeploymentData data = yMap.get(yDeploymentId);
        if (data != null && data.deleted == null) {
            data.deleted = Instant.now();
        }
    }

    public synchronized String toYorcId(String aDeploymentId) {
        return aMap.getOrDefault(aDeploymentId,DeploymentData.defVal).yorcId;
    }

    public synchronized String toAlienId(String yDeploymentId) {
        return yMap.getOrDefault(yDeploymentId,DeploymentData.defVal).alienId;
    }

    public void init() {
        // Schedule eviction task
        disposable = Completable.timer(configuration.getRegistryEvictionPerdiod(), TimeUnit.SECONDS, scheduler).subscribe(this::evictionTask);
    }

    public void term() {
        running.set(false);

        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void evictionTask() {
        if (running.get() == false) {
            return;
        }

        doEviction();

        // Reschedule eviction task
        if (running.get() == true) {
            disposable = Completable.timer( configuration.getRegistryEvictionPerdiod(), TimeUnit.SECONDS, scheduler).subscribe(this::evictionTask);
        }
    }

    private synchronized void doEviction() {
        int ttl = configuration.getRegistryEntryTtl();
        Instant now = Instant.now();
        for (DeploymentData data : yMap.values()) {
            if (data.deleted != null) {
                if (Duration.between(data.deleted,now).getSeconds() > ttl) {
                    yMap.remove(data.yorcId);
                    aMap.remove(data.alienId);
                }
            }
        }
    }
}
