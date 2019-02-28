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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LogEventPollingService {

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

        for (LogEvent logEvent : response.getLogs()) {

            if (log.isTraceEnabled()) {
                log.trace("Log received : {}", logEvent);
            }

            PaaSDeploymentLog paasLog = toPaasDeploymentLog(logEvent);
            if (paasLog != null) {
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
            Integer lastIndex = client.getLastIndex().blockingGet();

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
}
