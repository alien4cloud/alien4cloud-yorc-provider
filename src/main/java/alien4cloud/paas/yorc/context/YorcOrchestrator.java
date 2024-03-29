package alien4cloud.paas.yorc.context;

import alien4cloud.model.runtime.Execution;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.*;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.ServerClient;
import alien4cloud.paas.yorc.context.rest.TemplateManager;
import alien4cloud.paas.yorc.context.service.*;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvents;
import alien4cloud.paas.yorc.context.service.fsm.FsmMapper;
import alien4cloud.paas.yorc.context.service.fsm.FsmStates;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;
import alien4cloud.paas.yorc.exception.YorcInvalidStateException;
import alien4cloud.paas.yorc.location.AbstractLocationConfigurerFactory;
import alien4cloud.paas.yorc.service.PluginArchiveService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.*;

@Slf4j
@Component
public class YorcOrchestrator implements IOrchestratorPlugin<ProviderConfiguration> {

    @Inject
    @Getter
    private ApplicationContext context;

    @Inject
    private PluginArchiveService archiveService;

    @Inject
    private AbstractLocationConfigurerFactory yorcLocationConfigurerFactory;

    @Inject
    private TemplateManager templateManager;

    @Inject
    private EventPollingService eventPollingService;

    @Inject
    private LogEventPollingService logEventPollingService;

    @Inject
    private StateMachineService stateMachineService;

    @Inject
    private DeploymentClient deploymentClient;

    @Inject
    private ServerClient serverClient;

	@Inject
	private BusService busService;

	@Inject
	private InstanceInformationService instanceInformationService;

	@Inject
	private DeploymentRegistry registry;

	@Inject
    private DeployementCheckService checker;

    @Resource
    private ProviderConfiguration configuration;

    private final List<AbstractMonitorEvent> pendingEvents = Lists.newArrayList();

    private String yorcVersion;

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return yorcLocationConfigurerFactory.newInstance(locationType);
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        List<PluginArchive> archives = Lists.newArrayList();
        archives.add(archiveService.parsePluginArchives("commons/resources"));
        return archives;
    }

    @Override
    public void setConfiguration(String orchestratorId, ProviderConfiguration configuration) throws PluginConfigurationException {
        // nothing to do here, all is now handled by configuration.
    }

    @Override
    public Set<String> init(Map<String, String> activeDeployments) {
        if (log.isInfoEnabled())
            log.info("Initizing Yorc[{}] with {} active deployments", configuration.getOrchestratorName(),activeDeployments.size());

        yorcVersion = serverClient.getVersion().blockingGet();

        if (log.isInfoEnabled())
            log.info("Yorc[{}] Version is {}", configuration.getOrchestratorName(),yorcVersion);

        // Blocking REST call to build map
        // - Query all deployments
        // - Keep only known deployments
        // - Build a Map deploymentId -> FsmStates
        // - Build a Map deploymentId -> taskUrl
        Map<String, FsmStates> initialStates = Maps.newHashMap();
        deploymentClient.get()
            .filter(deployment -> activeDeployments.containsKey(deployment.getId()))
            .blockingForEach(deployment -> {
                String deploymentId = deployment.getId();
                initialStates.put(deploymentId, FsmMapper.fromYorcToFsmState(deployment.getStatus()));
            });

        // Start the deployementId registry
        registry.init();

        // Initialize InstanceInformationService
        instanceInformationService.init(initialStates.keySet());

        // Register Ids
        activeDeployments.forEach((yorcDeploymentId, deploymentId) -> registry.register(yorcDeploymentId, deploymentId));

		// Create the state machines for each deployment
        stateMachineService.newStateMachine(initialStates);

        // Set the deployment context for the active state machines
        activeDeployments.keySet().forEach(yorcDeploymentId -> {
            try {
                stateMachineService.setYorcDeploymentId(yorcDeploymentId);
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error(String.format("Fsm not found when setting context in fsm for deployment %s", yorcDeploymentId));
                }
            }
        });

        // Start Pollers
        eventPollingService.init();
        logEventPollingService.init();

        // Start the deployment checker
        checker.init();

        return initialStates.keySet();
    }

    public void term() {
        // Notify checker termination
        checker.term();

        // Notify Pollers that they have to stop
        eventPollingService.term();
        logEventPollingService.term();

        // Notify termination to Registry
        registry.term();

        // TemplateManager shutdown
        templateManager.term();
    }

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        stateMachineService.newStateMachine(deploymentContext.getDeploymentPaaSId());

        // Registering alienId to yorcId
        registry.register(deploymentContext.getDeploymentPaaSId(), deploymentContext.getDeploymentId());

        Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_STARTED, deploymentContext, callback);
        busService.publish(message);
    }

    @Override
    public void update(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.UPDATE_STARTED, deploymentContext, callback);
        busService.publish(message);
    }

    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback,boolean force) {
        Map<String,Object> params = Maps.newHashMap();
        params.put(StateMachineService.FORCE,force);

        Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.UNDEPLOYMENT_STARTED, deploymentContext, callback,params);
        busService.publish(message);
    }

    @Override
    public void purge(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.PURGE_STARTED, deploymentContext, callback);
        busService.publish(message);
    }

    @Override
    public void resume(PaaSDeploymentContext deploymentContext, Execution execution, IPaaSCallback<?> callback) {
        DeploymentStatus status = stateMachineService.getState(deploymentContext.getDeploymentPaaSId());

        String taskUrl = String.format("/deployments/%s/tasks/%s",deploymentContext.getDeployment().getOrchestratorDeploymentId(),execution.getId());

        switch(status) {
            case FAILURE:
                if (execution.getWorkflowName().equals("install")) {
                    Map<String,Object> params = Maps.newHashMap();
                    params.put(StateMachineService.TASK_URL, taskUrl);

                    Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.RESUME, deploymentContext, callback,params);
                    busService.publish(message);
                } else {
                    callback.onFailure(new YorcInvalidStateException("Can only resume install workflow in FAILURE state"));
                }
                break;
            case UNDEPLOYMENT_FAILURE:
                if (execution.getWorkflowName().equals("uninstall")) {
                    Map<String,Object> params = Maps.newHashMap();
                    params.put(StateMachineService.TASK_URL, taskUrl);

                    Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.RESUME, deploymentContext, callback,params);
                    busService.publish(message);
                } else {
                    callback.onFailure(new YorcInvalidStateException("Can only resume unsintall workflow in UNDEPLOYMENT_FAILURE state"));
                }
                break;
            case DEPLOYED:
            case UPDATED:

                if (execution.getWorkflowName().equals("install") || execution.getWorkflowName().equals("uninstall") ) {
                    callback.onFailure(new YorcInvalidStateException("Cannot resume worflow"));
                } else {
                    deploymentClient.resumeTask(taskUrl).subscribe(
                            () -> {
                                log.debug(String.format("Execution %s for deployment %s resumed", execution.getId(), deploymentContext.getDeployment().getOrchestratorDeploymentId()));
                            }
                            , callback::onFailure
                    );
                }
                break;
            default:
        }
    }

    @Override
    public void resetStep(PaaSDeploymentContext deploymentContext, Execution execution, String stepName, boolean done, IPaaSCallback<?> callback) {
        deploymentClient.resetStep(deploymentContext.getDeploymentPaaSId(),execution.getId(),stepName,done).subscribe(() -> {
            callback.onSuccess(null);
        }, callback::onFailure);
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        deploymentClient.scale(deploymentContext.getDeploymentPaaSId(),nodeTemplateId,instances).subscribe(s -> {
            log.info("Scaling Task: {}",s);
            callback.onSuccess(null);
        },callback::onFailure);
    }

    @Override
    public void launchWorkflow(PaaSDeploymentContext deploymentContext, String workflowName, Map<String, Object> inputs, IPaaSCallback<String> callback) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("Launching workflow %s for deployment %s", workflowName, deploymentContext.getDeploymentPaaSId()));
        }

        DeploymentStatus status = stateMachineService.getState(deploymentContext.getDeploymentPaaSId());
        switch(status) {
            case UNDEPLOYMENT_IN_PROGRESS:
            case DEPLOYMENT_IN_PROGRESS:
                callback.onFailure(new YorcInvalidStateException("Cannot start workflow while a deployment/undeployment is in progress"));
                return;
            default:
        }

        deploymentClient.executeWorkflow(deploymentContext.getDeploymentPaaSId(), workflowName, inputs,false).subscribe(s -> {
            if (log.isDebugEnabled()) {
                log.debug("Workflow {} launched for deployment {} : {}", workflowName, deploymentContext.getDeploymentPaaSId(), s);
            }
            String executionId = s.substring(s.lastIndexOf("/") + 1);
            callback.onSuccess(executionId);
        }, callback::onFailure);
    }

    @Override
    public void cancelTask(PaaSDeploymentContext deploymentContext, String taskId, IPaaSCallback<String> callback) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("Cancelling task %s for deployment %s", taskId, deploymentContext.getDeploymentPaaSId()));
        }

        deploymentClient.cancelTask(deploymentContext.getDeploymentPaaSId(),taskId).subscribe(
                () -> {
                    log.debug(String.format("Task %s for deployment %s cancelled", taskId, deploymentContext.getDeploymentPaaSId()));
                    callback.onSuccess(taskId);
            },callback::onFailure);
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        DeploymentStatus status = stateMachineService.getState(deploymentContext.getDeploymentPaaSId());
        callback.onSuccess(status);
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        instanceInformationService.getInformation(deploymentContext.getDeploymentPaaSId(),callback);
    }

    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventCallback) {
        AbstractMonitorEvent[] events;

        synchronized (pendingEvents) {
            events = pendingEvents.toArray(new AbstractMonitorEvent[0]);
            pendingEvents.clear();
        }

        eventCallback.onSuccess(events);

        if (log.isDebugEnabled() && events.length != 0) {
            log.debug(String.format("Successfully sent %d events to Alien", events.length));
        }
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback) throws OperationExecutionException {
        deploymentClient.executeOperation(deploymentContext.getDeploymentPaaSId(),request).subscribe(s -> {
            Map<String,String> customResults = Maps.newHashMap();
            customResults.put("result", "Succesfully execute custom " + request.getOperationName() + " on node " + request.getNodeTemplateName());
            callback.onSuccess(customResults);
        }, callback::onFailure);
    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean maintenanceModeOn) throws MaintenanceModeException {
        // TODO: implements (what to do?)
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeId, String instanceId, boolean maintenanceModeOn) throws MaintenanceModeException {
        // TODO: implements (what to do?)
    }

    /**
     * Post event to Alien
     * @param event
     */
    public void postAlienEvent(AbstractMonitorEvent event) {
        event.setDate((new Date()).getTime());
        event.setOrchestratorId(configuration.getOrchestratorId());

        synchronized (pendingEvents) {
            pendingEvents.add(event);
        }
    }

    /**
     * Maps Yorc DeploymentStatus in alien4cloud DeploymentStatus.
     * See yorc/deployments/structs.go to see all possible values
     * @param state
     * @return
     */
    public static DeploymentStatus getDeploymentStatusFromString(String state) {
        switch (state.toUpperCase()) {
            case "DEPLOYED":
                return DeploymentStatus.DEPLOYED;
            case "UNDEPLOYED":
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            case "PURGED":
                return DeploymentStatus.UNDEPLOYED;
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            case "UNDEPLOYMENT_IN_PROGRESS":
            case "PURGE_IN_PROGRESS":
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            case "INITIAL":
                return DeploymentStatus.INIT_DEPLOYMENT;
            case "DEPLOYMENT_FAILED":
                return DeploymentStatus.FAILURE;
            case "UNDEPLOYMENT_FAILED":
                return DeploymentStatus.UNDEPLOYMENT_FAILURE;
            case "UPDATE_IN_PROGRESS":
                return DeploymentStatus.UPDATE_IN_PROGRESS;
            case "UPDATED":
                return DeploymentStatus.UPDATED;
            case "UPDATE_FAILURE":
                return DeploymentStatus.UPDATE_FAILURE;
            case "PURGE_FAILED":
                return DeploymentStatus.PURGE_FAILURE;
            default:
                return DeploymentStatus.UNKNOWN;
        }
    }

    /**
     * Check if there is any running task according to the Yorc deployment state
     * @param status Yorc deployment state
     * @return True if existing running task
     */
    private boolean ifRunning(String status) {
        switch (status.toUpperCase()) {
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
            case "UNDEPLOYMENT_IN_PROGRESS":
            case "PURGE_IN_PROGRESS":
            case "UPDATE_IN_PROGRESS":
                return true;
            default:
                return false;
        }
    }

}
