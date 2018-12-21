package alien4cloud.paas.yorc.context.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.paas.model.AbstractPaaSWorkflowMonitorEvent;
import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import alien4cloud.paas.model.PaaSWorkflowStartedEvent;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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

        if (content != null && EVENT_HANDLER_PATTERN.get().matcher(content.replaceAll("\\n", "")).matches()) {
            if (content.startsWith("Start processing workflow")) {
                // -> PaasWorkflowStartedEvent
                PaaSWorkflowStartedEvent a4cEvent = new PaaSWorkflowStartedEvent();
                a4cEvent.setWorkflowName(event.getWorkflowId());
                postWorkflowMonitorEvent(a4cEvent, event);
            } else {
                TaskKey key = buildTaskKey(event);

                log.info("LOG: {} | {}",key,event.getContent());
            }
        }

        save(toPaasDeploymentLog(event));
    }

    private void postWorkflowMonitorEvent(AbstractPaaSWorkflowMonitorEvent a4cEvent, LogEvent logEvent) {
        a4cEvent.setExecutionId(logEvent.getExecutionId());
        a4cEvent.setWorkflowId(logEvent.getWorkflowId());
        a4cEvent.setDeploymentId(logEvent.getDeploymentId());
        orchestrator.postAlienEvent(a4cEvent);
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

    private TaskKey buildTaskKey(LogEvent event) {
        TaskKey result = null;

        if (StringUtils.isNotEmpty(event.getNodeId()) && StringUtils.isNotEmpty(event.getInterfaceName()) && StringUtils.isNotEmpty(event.getOperationName())) {
            result = new TaskKey(event.getNodeId(), event.getInstanceId(), event.getInterfaceName(), event.getOperationName());
        }
        return result;
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    private static class TaskKey {
        private final String nodeId;
        private final String instanceId;
        private final String interfaceName;
        private final String operationName;
    }
}
