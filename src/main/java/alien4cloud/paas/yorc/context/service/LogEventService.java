package alien4cloud.paas.yorc.context.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.paas.model.*;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LogEventService {

    /*
     * Regexp. The thread local should't be needed because access to the pattern are actually serialized
     */
    private static final String EVENT_HANDLER_REGEXP = "(.*Workflow.+ended without error.*|.*Start processing workflow.*|.*executing operation.*|.*executing delegate operation.*|.*operation succeeded.*|.*delegate operation succeeded.*|.*operation failed.*|.*delegate operation failed.*|.*Error .* happened in workflow .*)";
    private static final ThreadLocal<Pattern> EVENT_HANDLER_PATTERN = ThreadLocal.withInitial(() -> Pattern.compile(EVENT_HANDLER_REGEXP));

    @Inject
    private YorcOrchestrator orchestrator;

    @Inject
    private DeployementRegistry registry;

    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO dao;

    public void onEvent(LogEvent event) {
        String content = event.getContent();

        save(toPaasDeploymentLog(event));
    }

    private void save(PaaSDeploymentLog event) {
        dao.save(event);
    }

    private PaaSDeploymentLog toPaasDeploymentLog(final LogEvent logEvent) {
        PaaSDeploymentLog deploymentLog = new PaaSDeploymentLog();
        deploymentLog.setDeploymentId(registry.toAlienId(logEvent.getDeploymentId()));
        deploymentLog.setDeploymentPaaSId(logEvent.getDeploymentId());
        deploymentLog.setContent(logEvent.getContent());
        deploymentLog.setExecutionId(logEvent.getExecutionId());
        deploymentLog.setInstanceId(logEvent.getInstanceId());
        deploymentLog.setInterfaceName(logEvent.getInterfaceName());
        deploymentLog.setLevel(PaaSDeploymentLogLevel.fromLevel(logEvent.getLevel().toLowerCase()));
        deploymentLog.setType(logEvent.getType());
        deploymentLog.setNodeId(logEvent.getNodeId());
        deploymentLog.setTimestamp(logEvent.getDate());
        deploymentLog.setWorkflowId(logEvent.getWorkflowId());
        deploymentLog.setOperationName(logEvent.getOperationName());
        return deploymentLog;
    }
}
