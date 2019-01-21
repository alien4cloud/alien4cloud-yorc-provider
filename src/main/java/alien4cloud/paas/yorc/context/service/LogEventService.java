package alien4cloud.paas.yorc.context.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.paas.model.*;
import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;

@Slf4j
@Service
public class LogEventService {

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
        deploymentLog.setTaskId(logEvent.getAlienTaskId());
        return deploymentLog;
    }
}
