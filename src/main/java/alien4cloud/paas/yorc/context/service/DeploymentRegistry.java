package alien4cloud.paas.yorc.context.service;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeploymentRegistry {

    private BiMap<String,String> map = HashBiMap.create();

    public synchronized void register(String yorcDeploymentId, String deploymentId) {
            map.put(yorcDeploymentId, deploymentId);
    }

    public synchronized void unregister(String yorcDeploymentId) {
            map.remove(yorcDeploymentId);
    }

    public synchronized String toYorcId(String alienId) {
        return map.inverse().get(alienId);
    }

    public synchronized String toAlienId(String yorcId) {
        return map.get(yorcId);
    }

}
