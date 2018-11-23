package alien4cloud.paas.yorc.location;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.plugin.PluginManager;
import alien4cloud.plugin.model.ManagedPlugin;
import org.alien4cloud.tosca.catalog.ArchiveParser;
import org.springframework.context.ApplicationContext;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractLocationConfigurerFactory {

    @Inject
    protected ArchiveParser archiveParser;
    @Inject
    protected PluginManager pluginManager;
    @Inject
    protected ManagedPlugin selfContext;
    @Inject
    protected ApplicationContext applicationContext;

    public ILocationConfiguratorPlugin newInstance(String locationType) {

        ILocationConfiguratorPlugin locationConfigurer = newInstanceBasedOnLocation(locationType);
        if (locationConfigurer != null) {
            return locationConfigurer;
        }

        return new ILocationConfiguratorPlugin() {
            @Override
            public List<PluginArchive> pluginArchives() {
                return new ArrayList<>();
            }

            @Override
            public List<String> getResourcesTypes() {
                return new ArrayList<>();
            }

            @Override
            public Map<String, MatchingConfiguration> getMatchingConfigurations() {
                return new HashMap<>();
            }

            @Override
            public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
                return null;
            }


        };
    }

    protected abstract ILocationConfiguratorPlugin newInstanceBasedOnLocation(String locationType);
}
