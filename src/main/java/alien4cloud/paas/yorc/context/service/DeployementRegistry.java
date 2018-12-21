package alien4cloud.paas.yorc.context.service;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeployementRegistry {

    private BiMap<String,String> map = HashBiMap.create();

    public void register(String yorcId,String alienId) {
        synchronized(map) {
           map.put(yorcId,alienId);
        }
    }

    public void unregister(String yorcId) {
        synchronized(map) {
            map.remove(yorcId);
        }
    }

    public String toYorcId(String alienId) {
        synchronized (map) {
            return map.inverse().get(alienId);
        }
    }

    public String toAlienId(String yorcId) {
        synchronized (map) {
            return map.get(yorcId);
        }
    }
}
