package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.rest.LogEventClient;
import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import alien4cloud.paas.yorc.context.rest.response.LogEventDTO;
import alien4cloud.paas.yorc.dao.YorcESDao;
import alien4cloud.paas.yorc.model.LogEventIndex;
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
public class LogEventPollingService implements SelfNaming {

    @Inject
    private Scheduler scheduler;

    @Inject
    private YorcESDao dao;

    @Inject
    private YorcOrchestrator orchestrator;

    @Inject
    private LogEventClient client;

    @Inject
    private DeploymentRegistry registry;

    @Inject
    private BusService bus;

    @Resource
    private ProviderConfiguration configuration;

    /**
     * Index
     */
    private long index = 1;

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
            log.debug("Querying log events for orch <{}> from index <{}>", configuration.getOrchestratorId(), index);
        }
        client.get(index).subscribe(this::processEvents,this::processErrors);
    }

    /*
     * Process the Log event
     */
    private void processEvents(ResponseEntity<LogEventDTO> entity) {
        LogEventDTO response = entity.getBody();
        if (log.isDebugEnabled()) {
            log.debug("A batch of <{}> logs have been received for orch <{}> (from {})", response.getLogs().size(), configuration.getOrchestratorId(), response.getLast_index());
        }

        for (LogEvent logEvent : response.getLogs()) {

            if (log.isTraceEnabled()) {
                log.trace("Log received : {}", logEvent);
            }

            PaaSDeploymentLog paasLog = toPaasDeploymentLog(logEvent);
            if (paasLog != null) {
                totalCount.getAndIncrement();
                bus.publish(paasLog);
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
        LogEventIndex data = dao.findById(LogEventIndex.class,configuration.getOrchestratorId());
        if (data == null) {
            // This is our first run, initialize the index from Yorc
            Long lastIndex = client.getLastIndex().blockingGet();

            data = new LogEventIndex();
            data.setId(configuration.getOrchestratorId());
            data.setIndex(lastIndex);
            dao.save(data);
        }

        index = data.getIndex();
    }

    private void saveIndex() {
        dao.save(new LogEventIndex(configuration.getOrchestratorId(),index));
    }


    private PaaSDeploymentLog toPaasDeploymentLog(final LogEvent logEvent) {
        String alienId = registry.toAlienId(logEvent.getDeploymentId());
        if (alienId == null) {
            return null;
        }

        PaaSDeploymentLog deploymentLog = new PaaSDeploymentLog();
        deploymentLog.setDeploymentId(alienId);
        deploymentLog.setDeploymentPaaSId(logEvent.getDeploymentId());
        deploymentLog.setContent(logEvent.getContent());
        deploymentLog.setExecutionId(logEvent.getExecutionId());
        deploymentLog.setInstanceId(logEvent.getInstanceId());
        deploymentLog.setInterfaceName(logEvent.getInterfaceName());
        deploymentLog.setLevel(PaaSDeploymentLogLevel.fromLevel(logEvent.getLevel().toLowerCase()));
        deploymentLog.setType(logEvent.getType());
        deploymentLog.setNodeId(logEvent.getNodeId());
        deploymentLog.setTimestamp(logEvent.getDate());
        deploymentLog.setRawtimestamp(logEvent.getTimestamp());
        deploymentLog.setWorkflowId(logEvent.getWorkflowId());
        deploymentLog.setOperationName(logEvent.getOperationName());
        deploymentLog.setTaskId(logEvent.getAlienTaskId());

        return deploymentLog;
    }

    @ManagedAttribute
    public long getTotalLogEventCount() {
        return totalCount.get();
    }

    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException {
        Hashtable<String,String> kv = new Hashtable();
        kv.put("type","Orchestrators");
        kv.put("orchestratorName",configuration.getOrchestratorName());
        kv.put("name","LogEventPollingService");
        return new ObjectName("alien4cloud.paas.yorc",kv);
    }
}
