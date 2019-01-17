package alien4cloud.paas.yorc.context;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.rest.DeploymentClient;
import alien4cloud.paas.yorc.context.rest.TemplateManager;
import alien4cloud.paas.yorc.context.service.BusService;
import alien4cloud.paas.yorc.context.service.DeployementRegistry;
import alien4cloud.paas.yorc.context.service.EventPollingService;
import alien4cloud.paas.yorc.context.service.InstanceInformationService;
import alien4cloud.paas.yorc.context.service.LogEventPollingService;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvents;
import alien4cloud.paas.yorc.context.service.fsm.FsmMapper;
import alien4cloud.paas.yorc.context.service.fsm.FsmStates;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;
import alien4cloud.paas.yorc.location.AbstractLocationConfigurerFactory;
import alien4cloud.paas.yorc.service.PluginArchiveService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
	private BusService busService;

	@Inject
	private InstanceInformationService instanceInformationService;

	@Inject
	private DeployementRegistry registry;

	@Getter
    private String orchestratorId;

    private final List<AbstractMonitorEvent> pendingEvents = Lists.newArrayList();

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return yorcLocationConfigurerFactory.newInstance(locationType);
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        List<PluginArchive> archives = Lists.newArrayList();

        archives.add(archiveService.parsePluginArchives("commons/resources"));
        archives.add(archiveService.parsePluginArchives("docker/resources"));

        return archives;
    }

    @Override
    public void setConfiguration(String orchestratorId, ProviderConfiguration configuration) throws PluginConfigurationException {
        // Store orchestrator Id
        this.orchestratorId = orchestratorId;

        // Configure Rest Clients
        templateManager.configure(configuration);
    }

    @Override
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        if (log.isInfoEnabled())
            log.info("Init Yorc plugin for " + activeDeployments.size() + " active deployments");

        // Blocking REST call to build map
        // - Query all deployments
        // - Keep only known deployments
        // - Build a Map deploymentId -> FsmStates
        // - Build a Map deploymentId -> taskUrl
        Map<String, FsmStates> initialStates = Maps.newHashMap();
        Map<String, String> taskURLs = Maps.newHashMap();
        deploymentClient.get()
            .filter(deployment -> activeDeployments.containsKey(deployment.getId()))
            .blockingForEach(deployment -> {
                String deploymentId = deployment.getId();
                initialStates.put(deploymentId, FsmMapper.fromYorcToFsmState(deployment.getStatus()));
                if (ifRunning(deployment.getStatus())) {
                    String url = deploymentClient.getTaskURL(deploymentId).blockingGet();
                    taskURLs.put(deploymentId, url);
                }
            });

        // Initialize InstanceInformationService
        instanceInformationService.init(initialStates.keySet());

        // Register Ids
        for (PaaSTopologyDeploymentContext context : activeDeployments.values()) {
            registry.register(context);
        }

		// Create the state machines for each deployment
        stateMachineService.newStateMachine(initialStates);

        // Set the task url for the running deployment
        stateMachineService.setTaskUrl(taskURLs);

        // Start Pollers
        eventPollingService.init();
        logEventPollingService.init();
    }

    public void term() {
        // Notify Pollers that they have to stop
        eventPollingService.term();
        logEventPollingService.term();

        // TemplateManager shutdown
        templateManager.term();
    }

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        stateMachineService.newStateMachine(deploymentContext.getDeploymentPaaSId());

        // Registering alienId to yorcId
        registry.register(deploymentContext);

        Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.DEPLOYMENT_STARTED, deploymentContext, callback);
        busService.publish(message);
    }

    @Override
    public void update(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        // TODO: implements
    }

    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        Message<FsmEvents> message = stateMachineService.createMessage(FsmEvents.UNDEPLOYMENT_STARTED, deploymentContext, callback);
        busService.publish(message);
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        // TODO: implements
    }

    @Override
    public void launchWorkflow(PaaSDeploymentContext deploymentContext, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
        if (log.isInfoEnabled()) {
            log.info(String.format("Launching workflow %s for deployment %s", workflowName, deploymentContext.getDeploymentPaaSId()));
        }
        deploymentClient.executeWorkflow(deploymentContext.getDeploymentPaaSId(), workflowName, false).subscribe(s -> {}, callback::onFailure);
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

        if (events.length > 0) log.info("Get events returns {}",events.length);

        eventCallback.onSuccess(events);
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> operationResultCallback) throws OperationExecutionException {
        // TODO: implements
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
        event.setOrchestratorId(orchestratorId);

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
                return DeploymentStatus.UNDEPLOYED;
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            case "UNDEPLOYMENT_IN_PROGRESS":
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            case "INITIAL":
                return DeploymentStatus.INIT_DEPLOYMENT;
            case "DEPLOYMENT_FAILED":
            case "UNDEPLOYMENT_FAILED":
                return DeploymentStatus.FAILURE;
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
                return true;
            default:
                return false;
        }
    }

}
