package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.PaaSDeploymentContext;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeployementRegistry {

    private BiMap<String,String> map = HashBiMap.create();

    public synchronized void register(PaaSDeploymentContext context) {
            map.put(context.getDeploymentPaaSId(),context.getDeploymentId());
    }

    public synchronized void unregister(PaaSDeploymentContext context) {
            map.remove(context.getDeploymentPaaSId());
    }

    public synchronized String toYorcId(String alienId) {
        return map.inverse().get(alienId);
    }

    public synchronized String toAlienId(String yorcId) {
        return map.get(yorcId);
    }

}
