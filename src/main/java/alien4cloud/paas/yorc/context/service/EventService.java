package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.rest.EventClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.EventResponse;

import alien4cloud.paas.yorc.observer.CallbackObserver;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class EventService {

    @Inject
    protected Scheduler scheduler;

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
        // Bootstrap the query
        doQuery()
            .repeat()
            .retryWhen(this::retryHandler)
            .subscribe(this::processEvents);
    }

    /**
     * Do the query
     * @return
     */
    private Single<ResponseEntity<EventResponse>> doQuery() {
        return Single.defer( () -> {
            log.info("Querying Events with index {}",index);
            return client.getLogFromYorc(index);
        });
    }

    /*
     * Process the event
     */
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
    }

    /**
     * Broadcast event
     */
    private void broadcast(Event event) {
        log.debug("YORC EVT: {}",event);
        DeploymentInfo info = deploymentService.getDeployment(event.getDeployment_id());
        if (info != null) {
            info.getEventsAsSubject().onNext(event);
        }
    }

    /**
     * This will trigger a retry in case of error evry 2 seconds
     */
    private Flowable<Long> retryHandler(Flowable<Throwable> errors) {
        return errors.flatMap( e -> {
            log.error("Yorc Event Polling failure: {}",e.getMessage());
            return Flowable.timer(2, TimeUnit.SECONDS, scheduler);
        });
    }
}
