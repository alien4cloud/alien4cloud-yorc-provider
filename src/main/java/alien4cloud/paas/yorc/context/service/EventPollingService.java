package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
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
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.naming.SelfNaming;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@ManagedResource
public class EventPollingService implements SelfNaming {

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

    @Resource
    private ProviderConfiguration configuration;

    /**
     * Index
     */
    private int index = 1;

    /**
     * Stopped flag
     */
    private boolean stopped = false;

    /**
     * Total event count
     */
    private AtomicLong totalCount = new AtomicLong(0);

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
        if (log.isDebugEnabled()) {
            log.debug("Querying events for orch <{} from index <{}>", configuration.getOrchestratorId(), index);
        }
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
                case Event.EVT_CUSTOMCMD:
                case Event.EVT_SCALING:
                case Event.EVT_WORKFLOW:
                case Event.EVT_WORKFLOWSTEP:
                case Event.EVT_ALIENTASK:
                case Event.EVT_ATTRIBUTE:
                    totalCount.getAndIncrement();
                    bus.publish(event);
                    break;
                default:
                    if (log.isWarnEnabled())
                        log.warn("Unknown Yorc Event of type <{}> for deployment <{}> : {}", event.getType(), event.getDeploymentId(), event);
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
            Single.timer(configuration.getPollingRetryDelay(),TimeUnit.SECONDS,scheduler)
                .flatMap(x -> client.get(index))
                .subscribe(this::processEvents,this::processErrors);
        }
    }

    public void term() {
        stopped = true;
    }

    private void initIndex() {
        EventIndex data = dao.findById(EventIndex.class,configuration.getOrchestratorId());
        if (data == null) {
            // This is our first run, initialize the index from Yorc
            Integer lastIndex = client.getLastIndex().blockingGet();

            data = new EventIndex();
            data.setId(configuration.getOrchestratorId());
            data.setIndex(lastIndex);
            dao.save(data);
        }

        index = data.getIndex();
    }

    private void saveIndex() {
        dao.save(new EventIndex(configuration.getOrchestratorId(),index));
    }

    @ManagedAttribute
    public long getTotalEventCount() {
        return totalCount.get();
    }

    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException {
        Hashtable<String,String> kv = new Hashtable();
        kv.put("type","Orchestrators");
        kv.put("orchestratorName",configuration.getOrchestratorName());
        kv.put("name","EventPollingService");
        return new ObjectName("alien4cloud.paas.yorc",kv);
    }
}
