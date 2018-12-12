package alien4cloud.paas.yorc.context.service;

import java.util.Map;

import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvents;
import alien4cloud.paas.yorc.context.service.fsm.FsmMapper;
import io.reactivex.Scheduler;
import io.reactivex.functions.Consumer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

import alien4cloud.paas.yorc.context.rest.response.Event;
import io.reactivex.subjects.PublishSubject;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
@Service
public class BusService {

    @Inject
    private Scheduler scheduler;

    private static class Buses {
        private final PublishSubject<Event> events = PublishSubject.create();
        private final PublishSubject<LogEvent> logs = PublishSubject.create();
        private final PublishSubject<Message<FsmEvents>> messages = PublishSubject.create();
    }

    //TODO Concurrency?
    private Map<String, Buses> eventBuses = Maps.newHashMap();

    public void createEventBuses(String... ids) {
        for (String id : ids) {
            Buses b = new Buses();
            eventBuses.put(id, b);

            // Connect our buses
            //  - For now we log, coming on http pool
            b.events.subscribe(x -> log.info("EVT YORC: {}",x));

            //  - For now we log, coming on http pool
            b.events.subscribe(x -> log.info("LOG YORC: {}",x));

            //  - We also filter the event stream and link it to the messages stream
            //    It is subscribed on the task pool
            b.events.filter(FsmMapper::shouldMap).map(FsmMapper::map).observeOn(scheduler).subscribe(b.messages);
        }
    }

    public void subscribe(String deploymentId, Consumer<Message<FsmEvents>> callback) {
        eventBuses.get(deploymentId).messages.observeOn(scheduler).subscribe(callback);
    }

    public void publish(Event event) {
        if (eventBuses.containsKey(event.getDeployment_id())) {
            eventBuses.get(event.getDeployment_id()).events.onNext(event);
        }
    }

    public void publish(Message<FsmEvents> message) {
        String deploymentId = (String) message.getHeaders().get("deploymentId");

        if (eventBuses.containsKey(deploymentId)) {
            eventBuses.get(deploymentId).messages.onNext(message);
        }
    }
}
