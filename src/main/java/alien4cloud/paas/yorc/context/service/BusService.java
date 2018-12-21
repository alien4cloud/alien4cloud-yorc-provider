package alien4cloud.paas.yorc.context.service;

import java.util.Map;

import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvents;
import alien4cloud.paas.yorc.context.service.fsm.FsmMapper;
import io.reactivex.Scheduler;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.Subject;
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
        private final Subject<Event> events = PublishSubject.create();
        private final Subject<LogEvent> logs = PublishSubject.create();

        // Synchronize this one because onNext can be called by multiple threads
        private final Subject<Message<FsmEvents>> messages = PublishSubject.<Message<FsmEvents>>create().toSerialized();
    }

    //TODO Concurrency?
    private Map<String, Buses> eventBuses = Maps.newHashMap();

    public void createEventBuses(String... ids) {
        for (String id : ids) {
            Buses b = new Buses();
            eventBuses.put(id, b);

            //  - We also filter the event stream and link it to the messages stream
            //    It is subscribed on the task pool
            b.events.filter(FsmMapper::shouldMap).map(FsmMapper::map).observeOn(scheduler).subscribe(b.messages);
        }
    }

    public void subscribe(String deploymentId, Consumer<Message<FsmEvents>> callback) {
        eventBuses.get(deploymentId).messages.observeOn(scheduler).subscribe(callback);
    }

    public void subscribeEvents(String deploymentId, Consumer<Event> callback) {
        eventBuses.get(deploymentId).events.subscribe(callback);
    }

    public void subscribeLogs(String deploymentId, Consumer<LogEvent> callback) {
        eventBuses.get(deploymentId).logs.subscribe(callback);
    }

    public void publish(Event event) {
        if (eventBuses.containsKey(event.getDeployment_id())) {
            eventBuses.get(event.getDeployment_id()).events.onNext(event);
        }
    }

    public void publish(LogEvent logEvent) {
        if (eventBuses.containsKey(logEvent.getDeploymentId())) {
            eventBuses.get(logEvent.getDeploymentId()).logs.onNext(logEvent);
        }
    }

    public void publish(Message<FsmEvents> message) {
        String deploymentId = (String) message.getHeaders().get("deploymentId");

        if (eventBuses.containsKey(deploymentId)) {
            eventBuses.get(deploymentId).messages.onNext(message);
        }
    }
}
