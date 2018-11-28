package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.rest.EventClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.EventResponse;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class EventService {

    @Inject
    protected ScheduledExecutorService executorService;

    @Inject
    private EventClient client;

    private int index = 1;

    public void init() {
        // Bootstrap the service on task executor
        executorService.submit(this::queryEvents);
    }

    public void subscribe(String paasId,Consumer<Event> consumer) {

    }

    private void queryEvents() {
        log.debug("get events - index={}",index);

        ListenableFuture<ResponseEntity<EventResponse>> f = client.getLogFromYorc(index);
        f.addCallback(this::processEvents,this::processFailure);
    }

    private void processEvents(ResponseEntity<EventResponse> entity) {
        EventResponse response = entity.getBody();

        for (Event event : response.getEvents()) {
            log.info("Got Event for {} {}",event.getDeployment_id(),event.getType());
        }

        index = response.getLast_index();

        queryEvents();
    }

    private void processFailure(Throwable t) {
        if (t instanceof ReadTimeoutException) {
            // Reach long polling timeout , let's restart
            executorService.submit(this::queryEvents);
        } else {
            log.error("listening events fails: {}",t);

            // Something bad happen, we reschedule the polling later
            // to avoid a flood on yorc
            executorService.schedule(this::queryEvents,2, TimeUnit.SECONDS);
        }
    }
}
