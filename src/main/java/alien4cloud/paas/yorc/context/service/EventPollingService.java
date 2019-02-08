package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.rest.EventClient;
import alien4cloud.paas.yorc.context.rest.response.Event;
import alien4cloud.paas.yorc.context.rest.response.EventDTO;
import alien4cloud.paas.yorc.dao.YorcESDao;
import alien4cloud.paas.yorc.model.EventIndex;
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
    private BusService bus;

    @Inject
    private YorcESDao dao;

    @Inject
    private YorcOrchestrator orchestrator;

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

        // Ensure ES Index exists
        initIndex();

        // Bootstrap the polling
        doQuery();
    }

    /**
     * Do the query
     * @return
     */
    private void doQuery() {
        log.debug("Events Query - index={}", index);
        client.get(index).subscribe(this::processEvents,this::processErrors);
    }

    /*
     * Process the event
     */
    private void processEvents(ResponseEntity<EventDTO> entity) {
        EventDTO response = entity.getBody();

        for (Event event : response.getEvents()) {

            if (log.isTraceEnabled()) {
                log.trace("Event received : {}", event);
            }

            switch(event.getType()) {
                case Event.EVT_INSTANCE:
                case Event.EVT_DEPLOYMENT:
                case Event.EVT_OPERATION:
                case Event.EVT_SCALING:
                case Event.EVT_WORKFLOW:
                case Event.EVT_WORKFLOWSTEP:
                case Event.EVT_ALIENTASK:
                    bus.publish(event);
                    break;
                default:
                    if (log.isWarnEnabled())
                        log.warn("Unknown Yorc Event [{}/{}]", event.getType(), event.getDeploymentId());
            }
        }

        index = response.getLast_index();

        // store it in ES
        saveIndex();

        if (!stopped) {
            doQuery();
        }
    }

    private void processErrors(Throwable t) {
        if (!stopped) {
            if (log.isErrorEnabled())
                log.error("Event polling Exception: {}", t.getMessage());
            Single.timer(2,TimeUnit.SECONDS,scheduler)
                .flatMap(x -> client.get(index))
                .subscribe(this::processEvents,this::processErrors);
        }
    }

    public void term() {
        stopped = true;
    }

    private void initIndex() {
        EventIndex data = dao.findById(EventIndex.class,orchestrator.getOrchestratorId());
        if (data == null) {
            // This is our first run, initialize the index from Yorc
            Integer lastIndex = client.getLastIndex().blockingGet();

            data = new EventIndex();
            data.setId(orchestrator.getOrchestratorId());
            data.setIndex(lastIndex);
            dao.save(data);
        }

        index = data.getIndex();
    }

    private void saveIndex() {
        dao.save(new EventIndex(orchestrator.getOrchestratorId(),index));
    }
}
