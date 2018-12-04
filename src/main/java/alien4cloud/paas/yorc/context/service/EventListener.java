package alien4cloud.paas.yorc.context.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.google.common.collect.Maps;

import alien4cloud.paas.yorc.context.rest.response.Event;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class EventListener {

    // Event Consumers
    private Map<String,Consumer<Event>> consumers = Maps.newHashMap();

    // Throwable Consumers
    private Map<Class<?>,Consumer<Throwable>> handlers = Maps.newHashMap();

    private Disposable disposable;

    private Observable<Event> observable;

    private Scheduler scheduler;

    private long timeout;

    private TimeUnit timeUnit;

    private EventListener() {
    }

    public static class Builder {

        private final EventListener instance;

        private Builder() {
            instance = new EventListener();
        }

        public Builder when(String type, Consumer<Event> consumer) {
            instance.consumers.put(type, consumer);
            return this;
        }

        public Builder when(String type,String status,Consumer<Event> consumer) {
            String key = type + "/" + status;
            instance.consumers.put(key,consumer);
            return this;
        }

        public Builder withTimeout(long timeout,TimeUnit timeUnit,Consumer<Throwable> consumer) {
            instance.timeout = timeout;
            instance.timeUnit = timeUnit;

            instance.handlers.put(TimeoutException.class,consumer);

            return this;
        }

        public Builder observeOn(Scheduler scheduler) {
            instance.scheduler = scheduler;
            return this;
        }

        public EventListener build(Observable<Event> observable) {
            instance.observable = observable;
            return instance;
        }
    }

    /**
     * Create a builder
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the subscription
     */
    public void subscribe() {
        Observable<Event> o = observable;
        if (scheduler != null) {
            o = o.observeOn(scheduler);
            if (timeUnit != null) {
                o = o.timeout(timeout,timeUnit,scheduler);
            }
        } else {
            if (timeUnit != null) {
                o = o.timeout(timeout,timeUnit);
            }
        }
        disposable = o.subscribe(this::onEvent,this::onException);
    }

    /**
     * Cancel the subscription
     */
    public void cancel() {
        disposable.dispose();
    }

    /**
     * Dispatch events
     * @param event
     */
    private void onEvent(Event event) {
        //String key = event.getType() + "/" + event.getStatus();
        String key = event.getType();
        Consumer<Event> consumer = consumers.get(key);
        if (consumer != null) {
            consumer.accept(event);
        }
    }

    private void onException(Throwable throwable) {
        Consumer<Throwable> handler = handlers.get(throwable.getClass());
        if (handler != null) {
            handler.accept(throwable);
        } else {
            log.error("Exception not handled: {}",throwable);
        }
    }
}
