package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.rest.EventClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.EventDTO;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EventPollingService {

    @Inject
    protected Scheduler scheduler;

    @Inject
    private EventClient client;

    @Inject
    private EventBusService evenBusService;

    /**
     * Index
     */
    private int index = 1;

    /**
     * Stopped flag
     */
    private boolean stopped = false;

    /**
     * Initialize the polling
     */
    public void init() {
        doQuery();
    }

    /**
     * Do the query
     * @return
     */
    private void doQuery() {
        log.info("Querying Events with index {}", index);
        client.getLogFromYorc(index).subscribe(this::processEvents,this::processErrors);
    }

    /*
     * Process the event
     */
    private void processEvents(ResponseEntity<EventDTO> entity) {
        EventDTO response = entity.getBody();

        for (Event event : response.getEvents()) {
            switch(event.getType()) {
                case Event.EVT_INSTANCE:
                    //log.debug("Instance Event [{}/{}]",event.getType(),event.getDeployment_id());
                    break;
                case Event.EVT_DEPLOYMENT:
                case Event.EVT_OPERATION:
                case Event.EVT_SCALING:
                case Event.EVT_WORKFLOW:
                    log.error(String.format("Event received from Yorc %s", event));
                    evenBusService.publish(event);
                    break;
                default:
                    log.error ("Unknown Yorc Event [{}/{}]",event.getType(),event.getDeployment_id());
            }
        }

        index = response.getLast_index();

        if (!stopped) {
            doQuery();
        }
    }

    private void processErrors(Throwable t) {
        if (!stopped) {
            log.error("Event polling Exception: {}", t);
            Single.timer(2,TimeUnit.SECONDS,scheduler)
                .flatMap(x -> client.getLogFromYorc(index))
                .subscribe(this::processEvents,this::processErrors);
        }
    }

    public void term() {
        stopped = true;
    }

}