package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DeploymentService {

    @Inject
    private ApplicationContext context;

    private Lock lock = new ReentrantLock();

    private final Map<String,DeploymentInfo> map = Maps.newHashMap();

    public DeploymentInfo createDeployment(PaaSTopologyDeploymentContext deploymentContext) {
            return createDeployment(deploymentContext,DeploymentStatus.INIT_DEPLOYMENT);
    }

    public DeploymentInfo createDeployment(PaaSTopologyDeploymentContext deploymentContext,DeploymentStatus initialStatus) {
        DeploymentInfo info = (DeploymentInfo) context.getBean(DeploymentInfo.class);

        info.setContext(deploymentContext);
        info.setStatus(initialStatus);

        try {
            lock.lock();
            map.put(deploymentContext.getDeploymentPaaSId(), info);
        } finally {
            lock.unlock();
        }
        return info;
    }

    /**
     * @return snapshot of the deployments id
     */
    public Set<String> getDeployementIds() {
        try {
            lock.lock();
            return ImmutableSet.copyOf(map.keySet());
        } finally {
            lock.unlock();
        }
    }

    public DeploymentInfo getDeployment(String paasId) {
        try {
            lock.lock();
            return map.get(paasId);
        } finally {
            lock.unlock();
        }
    }
}
