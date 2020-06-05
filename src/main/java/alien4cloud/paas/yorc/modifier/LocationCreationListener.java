package alien4cloud.paas.yorc.modifier;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import org.alien4cloud.alm.deployment.configuration.flow.modifiers.FlowPhases;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import alien4cloud.model.orchestrators.Orchestrator;
import alien4cloud.model.orchestrators.locations.LocationModifierReference;
import alien4cloud.orchestrators.locations.events.AfterLocationCreated;
import alien4cloud.orchestrators.locations.services.LocationModifierService;
import alien4cloud.orchestrators.services.OrchestratorService;
import alien4cloud.paas.yorc.YorcPluginFactory;
import alien4cloud.plugin.model.ManagedPlugin;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@code LocationCreationListener} is used to register internal modifiers when a location is created.
 *
 * @author Loic Albertin
 */
@Component
@Slf4j
public class LocationCreationListener implements ApplicationListener<AfterLocationCreated> {

    @Inject
    private ManagedPlugin selfContext;

    @Resource
    private LocationModifierService locationModifierService;

    @Resource
    private OrchestratorService orchestratorService;

    private LocationModifierReference openstackFipModifierRef;
    private LocationModifierReference blockStorageWFModifierRef;
    private LocationModifierReference wfOperationHostModifierRef;
    private LocationModifierReference serviceTopologyModifierRef;
    private LocationModifierReference kubernetesTopologyModifierRef;
    private LocationModifierReference yorcKubernetesTopologyModifierRef;
    private LocationModifierReference googleAddressModifierRef;
    private LocationModifierReference googlePrivateNetworkModifierRef;
    private LocationModifierReference dockerToSingularityModifierRef;
    private LocationModifierReference yorcLocationModifierRef;
    private LocationModifierReference wfSimplifierModifierRef;

    @PostConstruct
    public synchronized void init() {
        openstackFipModifierRef = new LocationModifierReference();
        openstackFipModifierRef.setPluginId(selfContext.getPlugin().getId());
        openstackFipModifierRef.setBeanName(FipTopologyModifier.YORC_OPENSTACK_FIP_MODIFIER_TAG);
        openstackFipModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);

        blockStorageWFModifierRef = new LocationModifierReference();
        blockStorageWFModifierRef.setPluginId(selfContext.getPlugin().getId());
        blockStorageWFModifierRef.setBeanName(BlockStorageComputeWFModifier.YORC_BLOCK_STORAGE_WF_MODIFIER_TAG);
        blockStorageWFModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        wfOperationHostModifierRef = new LocationModifierReference();
        wfOperationHostModifierRef.setPluginId(selfContext.getPlugin().getId());
        wfOperationHostModifierRef.setBeanName(OperationHostModifier.YORC_WF_OPERATION_HOST_MODIFIER_TAG);
        wfOperationHostModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        serviceTopologyModifierRef = new LocationModifierReference();
        serviceTopologyModifierRef.setPluginId(selfContext.getPlugin().getId());
        serviceTopologyModifierRef.setBeanName(ServiceTopologyModifier.YORC_SERVICE_TOPOLOGY_MODIFIER_TAG);
        serviceTopologyModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        yorcKubernetesTopologyModifierRef = new LocationModifierReference();
        yorcKubernetesTopologyModifierRef.setPluginId(selfContext.getPlugin().getId());
        yorcKubernetesTopologyModifierRef.setBeanName(KubernetesTopologyModifier.YORC_KUBERNETES_MODIFIER_TAG);
        yorcKubernetesTopologyModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        kubernetesTopologyModifierRef = new LocationModifierReference();
        kubernetesTopologyModifierRef.setPluginId("alien4cloud-kubernetes-plugin");
        kubernetesTopologyModifierRef.setBeanName("kubernetes-modifier");
        // Set this modifier to PRE_POLICY_MATCH in order to trigger it after having replaced get_input functions
        kubernetesTopologyModifierRef.setPhase(FlowPhases.PRE_POLICY_MATCH);

        googleAddressModifierRef = new LocationModifierReference();
        googleAddressModifierRef.setPluginId(selfContext.getPlugin().getId());
        googleAddressModifierRef.setBeanName(GoogleAddressTopologyModifier.YORC_GOOGLE_ADDRESS_MODIFIER_TAG);
        googleAddressModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);

        googlePrivateNetworkModifierRef = new LocationModifierReference();
        googlePrivateNetworkModifierRef.setPluginId(selfContext.getPlugin().getId());
        googlePrivateNetworkModifierRef.setBeanName(GooglePrivateNetworkTopologyModifier.YORC_GOOGLE_PRIVATE_NETWORK_MODIFIER_TAG);
        googlePrivateNetworkModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);

        dockerToSingularityModifierRef = new LocationModifierReference();
        dockerToSingularityModifierRef.setPluginId(selfContext.getPlugin().getId());
        dockerToSingularityModifierRef.setBeanName(DockerToSingularityModifier.D2S_MODIFIER_TAG);
        dockerToSingularityModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);

        yorcLocationModifierRef = new LocationModifierReference();
        yorcLocationModifierRef.setPluginId(selfContext.getPlugin().getId());
        yorcLocationModifierRef.setBeanName(YorcLocationModifier.YORC_LOCATION_MODIFIER_TAG);
        yorcLocationModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        wfSimplifierModifierRef = new LocationModifierReference();
        wfSimplifierModifierRef.setPluginId(selfContext.getPlugin().getId());
        wfSimplifierModifierRef.setBeanName(SimplifierModifier.YORC_WF_SIMPLIFIER_TAG);
        wfSimplifierModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);
    }


    @Override
    public void onApplicationEvent(AfterLocationCreated event) {
        log.debug("Got location creation event for infrastructure type {}", event.getLocation().getInfrastructureType());

        Orchestrator orchestrator = orchestratorService.getOrFail(event.getLocation().getOrchestratorId());

        if (orchestrator.getPluginId().equals(selfContext.getPlugin().getId())) {
            if (YorcPluginFactory.OPENSTACK.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), openstackFipModifierRef);
            } else if (YorcPluginFactory.KUBERNETES.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), yorcKubernetesTopologyModifierRef);
                locationModifierService.add(event.getLocation(), kubernetesTopologyModifierRef);
            } else if (YorcPluginFactory.GOOGLE.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), googleAddressModifierRef);
                locationModifierService.add(event.getLocation(), googlePrivateNetworkModifierRef);
            } else if (YorcPluginFactory.SLURM.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), dockerToSingularityModifierRef);
            }
            locationModifierService.add(event.getLocation(), wfSimplifierModifierRef);
            locationModifierService.add(event.getLocation(), wfOperationHostModifierRef);
            locationModifierService.add(event.getLocation(), serviceTopologyModifierRef);
            locationModifierService.add(event.getLocation(), blockStorageWFModifierRef);
            locationModifierService.add(event.getLocation(), yorcLocationModifierRef);
        }
    }
}
