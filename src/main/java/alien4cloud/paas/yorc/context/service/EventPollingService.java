package alien4cloud.paas.yorc.context.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

import alien4cloud.paas.yorc.context.rest.EventClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.EventResponse;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class EventPollingService {

    @Inject
    protected ScheduledExecutorService executorService;

    @Inject
    private EventClient client;

    @Inject
    private EventBusService evenBusService;

    /**
     * Index
     */
    private int index = 1;

    /**
     * Initialize the polling
     */
    public void init() {
        // Bootstrap the evenBusService on task executor
        executorService.submit(this::queryEvents);
    }

    private void queryEvents() {
        log.debug("get events - index={}",index);

        ListenableFuture<ResponseEntity<EventResponse>> f = client.getLogFromYorc(index);
        f.addCallback(this::processEvents,this::processFailure);
    }

    private void processEvents(ResponseEntity<EventResponse> entity) {
        EventResponse response = entity.getBody();

        for (Event event : response.getEvents()) {
            switch(event.getType()) {
                case Event.EVT_INSTANCE:
                    //log.debug("Instance Event [{}/{}]",event.getType(),event.getDeployment_id());
                    break;
                case Event.EVT_DEPLOYMENT:
                case Event.EVT_OPERATION:
                case Event.EVT_SCALING:
                case Event.EVT_WORKFLOW:
                    log.debug("YORC EVENT [{}/{}]",event.getType(),event.getDeployment_id());
                    evenBusService.publish(event);
                    break;
                default:
                    log.warn ("Unknown Yorc Event [{}/{}]",event.getType(),event.getDeployment_id());
            }
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
