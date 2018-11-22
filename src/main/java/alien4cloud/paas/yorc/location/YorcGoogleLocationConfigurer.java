package alien4cloud.paas.yorc.location;


import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Scope("prototype")
public class YorcGoogleLocationConfigurer extends AbstractLocationConfigurer
{
    private static final String IMAGE_ID_PROP = "image";
    private static final String FLAVOR_ID_PROP = "machine_type";

    @Override
    public List<String> getResourcesTypes() {
        return getAllResourcesTypes();
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        return getMatchingConfigurations("google/resources-matching-config.yml");
    }

    @Override
    protected String[] getLocationArchivePaths() {
        return new String[]{"google/resources"};
    }

    @Override
    public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
        LocationResourceGeneratorService.ImageFlavorContext imageContext = resourceGeneratorService.buildContext("yorc.nodes.google.Image", "id", resourceAccessor);
        LocationResourceGeneratorService.ImageFlavorContext flavorContext = resourceGeneratorService.buildContext("yorc.nodes.google.MachineType", "id", resourceAccessor);
        boolean canProceed = true;

        if (CollectionUtils.isEmpty(imageContext.getTemplates())) {
            log.warn("At least one configured image resource is required for the auto-configuration");
            canProceed = false;
        }
        if (CollectionUtils.isEmpty(flavorContext.getTemplates())) {
            log.warn("At least one configured flavor resource is required for the auto-configuration");
            canProceed = false;
        }
        if (!canProceed) {
            log.warn("Skipping auto configuration");
            return null;
        }
        LocationResourceGeneratorService.ComputeContext computeContext = resourceGeneratorService
                .buildComputeContext("yorc.nodes.google.Compute", null, IMAGE_ID_PROP, FLAVOR_ID_PROP, resourceAccessor);

        return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, null, resourceAccessor);
    }
}
