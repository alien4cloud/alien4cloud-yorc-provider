package alien4cloud.paas.yorc.context.service;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

import alien4cloud.paas.yorc.context.rest.response.Event;
import io.reactivex.subjects.PublishSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EventBusService {

    //TODO Concurrency?
    private Map<String, PublishSubject<Event>> eventBuses = Maps.newHashMap();

    public void createEventBus(String... ids) {
        for (String id : ids) {
            eventBuses.put(id, PublishSubject.create());
        }
    }

    public void subscribe(String deploymentId, String topic, Consumer<Event> callback) {
        EventListener.builder().when(topic, callback).build(eventBuses.get(deploymentId)).subscribe();
    }

    public void publish(Event event) {
        if (eventBuses.containsKey(event.getDeployment_id())) {
            eventBuses.get(event.getDeployment_id()).onNext(event);
        } else {
            log.error(String.format("Event bus related to %s does not exist.", event.getDeployment_id()));
        }
    }
}
