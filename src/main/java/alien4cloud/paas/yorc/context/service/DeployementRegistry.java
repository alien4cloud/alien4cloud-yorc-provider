package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class DeployementRegistry {

    private Lock lock = new ReentrantLock();

    private BiMap<String,String> map = HashBiMap.create();

    public void register(PaaSDeploymentContext context) {
        lock.lock();
        try {
            map.put(context.getDeploymentPaaSId(),context.getDeploymentId());
        } finally {
            lock.unlock();
        }
    }

    public void unregister(PaaSDeploymentContext context) {
        lock.lock();
        try {
            map.remove(context.getDeploymentPaaSId());
        } finally {
            lock.unlock();
        }
    }

    public String toYorcId(String alienId) {
        lock.lock();
        try {
            return map.inverse().get(alienId);
        } finally {
            lock.unlock();
        }
    }

    public String toAlienId(String yorcId) {
        lock.lock();
        try {
            return map.get(yorcId);
        } finally {
            lock.unlock();
        }
    }

}
