package alien4cloud.paas.yorc.location;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Configure resources for the maas  location type.
 */
@Slf4j
@Component
@Scope("prototype")
public class YorcMaasLocationConfigurer extends AbstractLocationConfigurer {
    
    private static final String IMAGE_ID_PROP = "image_id";
    private static final String FLAVOR_ID_PROP = "instance_type";
    
    @Override
    public List<String> getResourcesTypes() {
        return getAllResourcesTypes();
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        return getMatchingConfigurations("maas/resources-matching-config.yml");
    }

    @Override
    protected String[] getLocationArchivePaths() {
        return new String[]{"maas/resources"};
    }

}
