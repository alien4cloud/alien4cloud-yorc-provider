package alien4cloud.paas.yorc;

import alien4cloud.model.orchestrators.ArtifactSupport;
import alien4cloud.model.orchestrators.locations.LocationSupport;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.context.YorcOrchestrator;
import alien4cloud.paas.yorc.context.YorcOrchestratorConfiguration;
import alien4cloud.paas.yorc.dao.YorcESDao;
import alien4cloud.paas.yorc.location.AbstractLocationConfigurerFactory;
import alien4cloud.paas.yorc.model.EventIndex;
import alien4cloud.paas.yorc.model.LogEventIndex;
import alien4cloud.utils.ClassLoaderUtil;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.normative.types.ToscaTypes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.Map;

@Slf4j
public class YorcPluginFactory implements IOrchestratorPluginFactory<YorcOrchestrator,ProviderConfiguration> {

    /**
     * Locations
     */
    public static final String OPENSTACK = "OpenStack";
    public static final String SLURM = "Slurm";
    public static final String KUBERNETES = "Kubernetes";
    public static final String AWS = "AWS";
    public static final String GOOGLE = "Google Cloud";
    public static final String HOSTS_POOL = "HostsPool";

    public static final String MONITORING_TIME_INTERVAL = "monitoring_time_interval";

    /**
     * Plugin Context
     */
    @Resource
    private ApplicationContext pluginContext;

    @Inject
    private YorcESDao dao;

    @Inject
    private AbstractLocationConfigurerFactory yorcLocationConfigurerFactory;

    /**
     * Deployment properties
     */
    private final Map<String, PropertyDefinition> deploymentProperties = buildDeploymentProperties();

    @Override
    public YorcOrchestrator newInstance(ProviderConfiguration configuration) {
        final ProviderConfiguration effectiveConfiguration = configuration != null ? configuration : new ProviderConfiguration();

        AnnotationConfigApplicationContext orchestratorContext = new AnnotationConfigApplicationContext();

        orchestratorContext.setParent(pluginContext);
        orchestratorContext.setClassLoader(pluginContext.getClassLoader());

        ClassLoaderUtil.runWithContextClassLoader(pluginContext.getClassLoader(), () -> {
            orchestratorContext.getBeanFactory().registerResolvableDependency(ProviderConfiguration.class, effectiveConfiguration);
            orchestratorContext.register(YorcOrchestratorConfiguration.class);
            orchestratorContext.refresh();
        });

        log.debug("Yorc Context Created: {} Plugin Context: {}", orchestratorContext.getId() , pluginContext.getId());

        YorcOrchestrator orchestrator = (YorcOrchestrator) orchestratorContext.getBean(YorcOrchestrator.class);

        return orchestrator;
    }

    @Override
    public void destroy(YorcOrchestrator instance) {
        // Terminate the instance
        instance.term();

        // Then close the associated spring context
        AnnotationConfigApplicationContext context = (AnnotationConfigApplicationContext) instance.getContext();
        context.close();
    }

    @Override
    public ProviderConfiguration getDefaultConfiguration() {
        return new ProviderConfiguration();
    }

    @Override
    public Class<ProviderConfiguration> getConfigurationType() {
        return ProviderConfiguration.class;
    }

    @Override
    public LocationSupport getLocationSupport() {
        return new LocationSupport(true , new String[]{
                GOOGLE,
                AWS,
                OPENSTACK,
                SLURM,
                KUBERNETES,
                HOSTS_POOL
            });
    }

    @Override
    public ArtifactSupport getArtifactSupport() {
        // support all type of implementations artifacts
        return new ArtifactSupport(new String[]{
                "tosca.artifacts.Implementation.Python",
                "tosca.artifacts.Implementation.Bash",
                "tosca.artifacts.Implementation.Ansible",
                "tosca.artifacts.Deployment.Image.Container.Docker",
                "yorc.artifacts.Deployment.SlurmJob",
                // FIXME: temporally added but should probably be defined @ location instance level
                "alien.artifacts.AnsiblePlaybook",
                "org.alien4cloud.artifacts.AnsiblePlaybook",
                "org.alien4cloud.artifacts.GangjaConfig"
            });
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyDefinitions() {
        return deploymentProperties;
    }

    @Override
    public String getType() {
        return "Yorc Orchestrator";
    }

    public Map<String, PropertyDefinition> buildDeploymentProperties() {
        Map<String, PropertyDefinition> props = Maps.newHashMap();

        // Monitoring time interval
        PropertyDefinition monitoringInterval = new PropertyDefinition();
        monitoringInterval.setType(ToscaTypes.INTEGER.toString());
        monitoringInterval.setRequired(false);
        monitoringInterval.setDescription("This enables a liveness computes and services monitoring and defines the time interval in seconds between the checks.");
        monitoringInterval.setDefault(new ScalarPropertyValue("0"));
        props.put(MONITORING_TIME_INTERVAL, monitoringInterval);

        return props;
    }

    @Override
    public void delete(String id) {
        dao.delete(LogEventIndex.class,id);
        dao.delete(EventIndex.class,id);
    }

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return yorcLocationConfigurerFactory.newInstance(locationType);
    }
}
