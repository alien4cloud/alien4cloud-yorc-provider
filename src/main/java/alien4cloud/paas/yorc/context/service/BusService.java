package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvents;
import alien4cloud.paas.yorc.context.service.fsm.FsmMapper;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;
import com.google.common.collect.Maps;
import io.reactivex.Scheduler;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Map;

@Slf4j
@Service
public class BusService {

    @Inject
    private Scheduler scheduler;

    private static class Buses {
        private Subject<Event> events = PublishSubject.create();

        // Synchronize this one because onNext can be called by multiple threads
        private Subject<Message<FsmEvents>> messages = PublishSubject.<Message<FsmEvents>>create().toSerialized();
    }

    private Subject<PaaSDeploymentLog> logs = PublishSubject.create();

    private Map<String, Buses> eventBuses = Maps.newConcurrentMap();

    public void createEventBuses(String... ids) {
        for (String id : ids) {
            Buses b = new Buses();
            eventBuses.put(id, b);

            //  - We also filter the event stream and link it to the messages stream
            //    It is subscribed on the task pool
            b.events.filter(FsmMapper::shouldMap).map(FsmMapper::map).observeOn(scheduler).subscribe(b.messages);
        }
    }

    public void deleteEventBuses(String deploymentId) {
        eventBuses.remove(deploymentId);
    }

    public void subscribe(String deploymentId, Consumer<Message<FsmEvents>> callback) {
        eventBuses.get(deploymentId).messages.observeOn(scheduler).subscribe(callback);
    }

    public void subscribeEvents(String deploymentId, Consumer<Event> callback) {
        eventBuses.get(deploymentId).events.subscribe(callback);
    }

    public void subscribeLogs(Consumer<PaaSDeploymentLog> callback) {
        logs.subscribe(callback);
    }

    public void unsubscribeEvents(String deploymentId) {
        Buses b = eventBuses.get(deploymentId);
        if (b != null) {
            b.events = null;
        }
    }

    public void publish(Event event) {
        Buses b = eventBuses.get(event.getDeploymentId());
        if (b != null && b.events != null) {
            b.events.onNext(event);
        }
    }

    public void publish(PaaSDeploymentLog logEvent) {
        logs.onNext(logEvent);
    }

    public void publish(Message<FsmEvents> message) {
        String deploymentId = (String) message.getHeaders().get(StateMachineService.YORC_DEPLOYMENT_ID);

        Buses b = eventBuses.get(deploymentId);
        if (b != null && b.messages != null) {
            b.messages.onNext(message);
        }
    }
}
