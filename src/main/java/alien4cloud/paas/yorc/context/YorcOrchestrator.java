package alien4cloud.paas.yorc.context;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

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
import alien4cloud.paas.yorc.context.service.EventBusService;
import alien4cloud.paas.yorc.context.service.EventPollingService;
import alien4cloud.paas.yorc.context.service.fsm.FsmEvent;
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
    private EventPollingService eventService;

    @Inject
    private StateMachineService stateMachineService;

    @Inject
    private DeploymentClient deploymentClient;

    private ProviderConfiguration configuration;

	@Inject
	private EventBusService eventBusService;


    /**
     * TODO: Provisoire
     *  Pour faciliter la mise en oeuvre
     */
    private Map<String,PaaSTopologyDeploymentContext> trickActiveDeployments;


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
        // Configure Rest Clients
        templateManager.configure(configuration);
    }

    @Override
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        log.info("Init Yorc plugin for " + activeDeployments.size() + " active deployments");


        //doUpdateDeploymentInfo();

		// Create the state machines for each deployment
		stateMachineService.newStateMachine(activeDeployments.keySet().toArray(new String[0]));

        // Start services
        eventService.init();
    }

    public void term() {
        eventService.term();
    }

    @Override
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        stateMachineService.newStateMachine(deploymentContext.getDeploymentPaaSId());
        eventBusService.publish(new FsmEvent(deploymentContext.getDeploymentPaaSId(), FsmEvent.DeploymentMessages.DEPLOYMENT_STARTED,
				ImmutableMap.of("callback", callback, "deploymentContext", deploymentContext)));
    }

    @Override
    public void update(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        // TODO: implements
    }

    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        // TODO: implements
    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        // TODO: implements
    }

    @Override
    public void launchWorkflow(PaaSDeploymentContext deploymentContext, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
        // TODO: implements
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        /*DeploymentInfo info = deploymentService.getDeployment(deploymentContext.getDeploymentPaaSId());
        if (info != null) {
            // TODO: get the info from the info itself
            deploymentClient.getStatus(deploymentContext.getDeploymentPaaSId())
                .map(YorcOrchestrator::getDeploymentStatusFromString)
                .subscribe(
                       status ->  callback.onSuccess(status),
                       throwable -> {
                            if (RestUtil.isHttpError(throwable,HttpStatus.NOT_FOUND)) {
                                callback.onSuccess(DeploymentStatus.UNDEPLOYED);
                            } else {
                                callback.onFailure(throwable);
                            }
                       }
                );
        } else {
            callback.onSuccess(DeploymentStatus.UNDEPLOYED);
        }*/
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        // TODO: implements
        log.error("TODO: getInstancesInformation");
    }

    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventCallback) {
        // TODO: implements
        log.error("TODO: getEventsSince");
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> operationResultCallback) throws OperationExecutionException {
        // TODO: implements
    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean maintenanceModeOn) throws MaintenanceModeException {
        // TODO: implements
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeId, String instanceId, boolean maintenanceModeOn) throws MaintenanceModeException {
        // TODO: implements
    }

    /**
     * Maps Yorc DeploymentStatus in alien4cloud DeploymentStatus.
     * See yorc/deployments/structs.go to see all possible values
     * @param state
     * @return
     */
    private static DeploymentStatus getDeploymentStatusFromString(String state) {
        switch (state) {
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
     * Blocking call that update deployment infos
     */
    private void doUpdateDeploymentInfo() {
        /*
        Set<String> deploymentIds = deploymentService.getDeployementIds();

        Observable<String> links = Observable.fromIterable(deploymentService.getDeployementIds())
                .map(url -> "/deployments/" + url);

        Browser.browserFor( links, url -> deploymentClient.queryUrl(url,DeploymentDTO.class),2)
                .flatMap( agg -> agg.follow("node", url -> deploymentClient.queryUrl(url,NodeDTO.class),2))
                .flatMap( agg -> agg.follow("instance", url -> deploymentClient.queryUrl(url,InstanceDTO.class),2))
                .flatMap( agg -> agg.follow("attribute", url -> deploymentClient.queryUrl(url,AttributeDTO.class),2))
                .blockingSubscribe( ctx -> {
                    DeploymentDTO deployment = (DeploymentDTO) ctx.get(0);
                    NodeDTO node = (NodeDTO) ctx.get(1);
                    InstanceDTO instance = (InstanceDTO) ctx.get(2);
                    AttributeDTO attribute = (AttributeDTO) ctx.get(3);

                    log.info("ATTRIBUTE: {}/{}/{}/{}={}",deployment.getId(),node.getName(),instance.getId(),attribute.getName(),attribute.getValue());
                });

        log.info("----------------");
        */
    }
}
