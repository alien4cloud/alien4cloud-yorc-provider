package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.rest.response.Event;
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

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BusService {

    @Resource
    private ProviderConfiguration configuration;

    @Inject
    private Scheduler scheduler;

    /**
     *      Per deployment we have:
     *      - One event bus
     *      - One log bus
     *      - One FSM Messages bus
     *
     *      The event bus and log bus are connected to the FSM Message bus.
     *
     *      There is a global log bus that is used for logs serialization in ES
     */
    private static class Buses {
        // Event Bus
        private Subject<Event> evts = PublishSubject.create();

        // Log Bus
        private Subject<PaaSDeploymentLog> logs = PublishSubject.create();

        // Synchronize this one because onNext can be called by multiple threads
        private Subject<Message<FsmEvents>> messages = PublishSubject.<Message<FsmEvents>>create().toSerialized();
    }

    private Subject<PaaSDeploymentLog> logs = PublishSubject.create();

    private Map<String, Buses> eventBuses = Maps.newConcurrentMap();

    public void createEventBuses(String... ids) {
        for (String id : ids) {
            Buses b = new Buses();
            eventBuses.put(id, b);

            // Connect buses to the FSM Message Bus
            b.evts.filter(FsmMapper::shouldMap).map(FsmMapper::map).observeOn(scheduler).subscribe(b.messages);
            b.logs.filter(FsmMapper::shouldMap).map(FsmMapper::map).observeOn(scheduler).subscribe(b.messages);
        }
    }

    public void deleteEventBuses(String deploymentId) {
        eventBuses.remove(deploymentId);
    }

    public void subscribe(String deploymentId, Consumer<Message<FsmEvents>> callback) {
        eventBuses.get(deploymentId).messages.observeOn(scheduler).subscribe(callback);
    }

    public void subscribeEvents(String deploymentId, Consumer<Event> callback) {
        eventBuses.get(deploymentId).evts.subscribe(callback);
    }

    public void subscribeLogs(Consumer<List<PaaSDeploymentLog>> callback) {
        logs.buffer(configuration.getLogBufferDelay(), TimeUnit.MILLISECONDS,scheduler,configuration.getLogBufferCount()).subscribe(callback);
    }

    public void unsubscribeEvents(String deploymentId) {
        Buses b = eventBuses.get(deploymentId);
        if (b != null) {
            b.evts = null;
        }
    }

    public void publish(Event event) {
        Buses b = eventBuses.get(event.getDeploymentId());
        if (b != null && b.evts != null) {
            b.evts.onNext(event);
        }
    }

    public void publish(PaaSDeploymentLog logEvent) {
        Buses b = eventBuses.get(logEvent.getDeploymentPaaSId());
        if (b != null && b.logs != null) {
            b.logs.onNext(logEvent);
        }
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
