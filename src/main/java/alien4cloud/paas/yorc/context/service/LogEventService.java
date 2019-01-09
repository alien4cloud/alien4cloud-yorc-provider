package alien4cloud.paas.yorc.context.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.paas.model.*;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.model.workflow.activities.CallOperationWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.DelegateWorkflowActivity;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    // Mapping
    private Map<String, Map<TaskKey, String>> mapTaskIds = Maps.newHashMap();

    public void onEvent(LogEvent event) {
        String content = event.getContent();

        if (content != null && EVENT_HANDLER_PATTERN.get().matcher(content.replaceAll("\\n", "")).matches()) {

            if (content.equals("executing operation") || content.equals("executing delegate operation")) {
                String taskId = getOrCreateTaskId(event);
                String stepId = getStepId(event);

                if (stepId != null) {
                    WorkflowStepStartedEvent workflowStepStartedEvent = new WorkflowStepStartedEvent();
                    workflowStepStartedEvent.setStepId(stepId);
                    postWorkflowStepEvent(workflowStepStartedEvent, event);
                }

                // a task has been sent ...
                TaskSentEvent taskSentEvent = new TaskSentEvent();
                taskSentEvent.setTaskId(taskId);
                taskSentEvent.setWorkflowStepId(stepId);
                postTaskEvent(taskSentEvent, event);
                // ... and started
                TaskStartedEvent taskStartedEvent = new TaskStartedEvent();
                taskStartedEvent.setTaskId(taskId);
                taskStartedEvent.setWorkflowStepId(stepId);
                postTaskEvent(taskStartedEvent, event);

            } else if (content.startsWith("Start processing workflow")) {
                // -> PaasWorkflowStartedEvent
                PaaSWorkflowStartedEvent a4cEvent = new PaaSWorkflowStartedEvent();
                a4cEvent.setWorkflowName(event.getWorkflowId());
                postWorkflowMonitorEvent(a4cEvent, event);
            } else if (content.endsWith("ended without error")) {
                // -> PaasWorkflowSucceededEvent
                PaaSWorkflowSucceededEvent a4cEvent = new PaaSWorkflowSucceededEvent();
                postWorkflowMonitorEvent(a4cEvent, event);

                // TODO: update registration
            } else if (content.equals("operation succeeded") || content.equals("delegate operation succeeded") ) {
                String taskId = getOrCreateTaskId(event);
                String stepId = getStepId(event);

                // -> TaskSucceedeEvent
                TaskSucceededEvent taskSucceededEvent = new TaskSucceededEvent();
                taskSucceededEvent.setTaskId(taskId);
                postTaskEvent(taskSucceededEvent, event);

                if (stepId != null) {
                    WorkflowStepCompletedEvent workflowStepCompletedEvent = new WorkflowStepCompletedEvent();
                    workflowStepCompletedEvent.setStepId(stepId);
                    postWorkflowStepEvent(workflowStepCompletedEvent, event);
                }
            } else {
                TaskKey key = buildTaskKey(event);

                log.info("LOG: {} | {}",key,event.getContent());
            }
        }

        save(toPaasDeploymentLog(event));
    }

    private void postWorkflowStepEvent(AbstractWorkflowStepEvent a4cEvent, LogEvent logEvent) {
        a4cEvent.setNodeId(logEvent.getNodeId());
        a4cEvent.setInstanceId(logEvent.getInstanceId());
        a4cEvent.setOperationName(logEvent.getInterfaceName() + "." + logEvent.getOperationName());
        postWorkflowMonitorEvent(a4cEvent, logEvent);
    }

    private void postTaskEvent(AbstractTaskEvent a4cEvent, LogEvent logEvent) {
        a4cEvent.setNodeId(logEvent.getNodeId());
        a4cEvent.setInstanceId(logEvent.getInstanceId());
        a4cEvent.setOperationName(logEvent.getInterfaceName() + "." + logEvent.getOperationName());
        postWorkflowMonitorEvent(a4cEvent, logEvent);
    }

    private void postWorkflowMonitorEvent(AbstractPaaSWorkflowMonitorEvent a4cEvent, LogEvent logEvent) {
        a4cEvent.setExecutionId(logEvent.getExecutionId());
        a4cEvent.setWorkflowId(logEvent.getWorkflowId());
        a4cEvent.setDeploymentId(registry.toAlienId(logEvent.getDeploymentId()));
        orchestrator.postAlienEvent(a4cEvent);
    }

    private void save(PaaSDeploymentLog event) {
        dao.save(event);
    }

    private String getOrCreateTaskId(LogEvent event) {
        Map<TaskKey,String> taskIds = mapTaskIds.computeIfAbsent(event.getDeploymentId(),key -> Maps.newHashMap());

        TaskKey key = buildTaskKey(event);
        if (key != null) {
            String taskId = UUID.randomUUID().toString();
            taskIds.put(key, taskId);
            return taskId;
        } else {
            return null;
        }
    }

    private String getStepId(LogEvent event) {
        Topology topology = registry.getTopology(event.getDeploymentId());

        if (topology == null) {
            return null;
        }

        Workflow wf = topology.getWorkflow(event.getWorkflowId());
        if (wf == null) {
            return null;
        }

        Optional<WorkflowStep> step = wf.getSteps().values().stream().filter( wfs -> {
                if (wfs.getTarget().equals(event.getNodeId())) {
                    if (wfs.getActivity() instanceof CallOperationWorkflowActivity) {
                        CallOperationWorkflowActivity activity = (CallOperationWorkflowActivity) wfs.getActivity();
                        // FIXME : don't try to match onto interfaceName since it's not the same (configure vs Configure)
                        if (/*activity.getInterfaceName().equals(pLogEvent.getInterfaceName()) &&*/
                                activity.getOperationName().equals(event.getOperationName())) {
                            return true;
                        }
                    } else if (event.getInterfaceName().equals("delegate") && wfs.getActivity() instanceof DelegateWorkflowActivity) {
                        DelegateWorkflowActivity activity = (DelegateWorkflowActivity)wfs.getActivity();
                        if (event.getOperationName().equals(activity.getDelegate())) {
                            return true;
                        }
                    }
                }
                return false;
            }).findFirst();

        if (step.isPresent()) {
            return step.get().getName();
        }
        return null;
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
