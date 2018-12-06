package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.rest.EventClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.EventResponse;

import alien4cloud.paas.yorc.observer.CallbackObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class EventService {

    @Inject
    protected ScheduledExecutorService executorService;

    @Inject
    private EventClient client;

    @Inject
    private DeploymentService deploymentService;

    /**
     * Index
     */
    private int index = 1;

    /**
     * Initialize the polling
     */
    public void init() {
        // Bootstrap the service on task executor
        executorService.submit(this::queryEvents);
    }

    private void queryEvents() {
        log.debug("get events - index={}",index);

        client.getLogFromYorc(index).subscribe(
                new CallbackObserver<>(this::processEvents,this::processFailure)
        );
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
                    broadcast(event);
                    break;
                default:
                    log.warn ("Unknown Yorc Event [{}/{}]",event.getType(),event.getDeployment_id());
            }
        }

        index = response.getLast_index();

        queryEvents();
    }

    private void processFailure(Throwable t) {
        log.error("listening events fails: {}",t);

        // Something bad happen, we reschedule the polling later
        // to avoid a flood on yorc
        executorService.schedule(this::queryEvents,2, TimeUnit.SECONDS);
    }

    private void broadcast(Event event) {
        log.debug("YORC EVT: {}",event);
        DeploymentInfo info = deploymentService.getDeployment(event.getDeployment_id());
        if (info != null) {
            info.getEventsAsSubject().onNext(event);
        }
    }
}
