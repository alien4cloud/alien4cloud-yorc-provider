package alien4cloud.paas.yorc.modifier;

import static alien4cloud.utils.AlienUtils.safe;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.Topology;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.model.orchestrators.locations.Location;

/**
 * YorcLocationModifier allow to set the location metadata on nodes to indicate
 * on which location a node should be deployed. If not set Yorc will select a
 * random location of the proper type for the node.
 *
 * Current algorithm is:
 * First check if the a4c location defines meta-property YORC_LOCATION, if so use it.
 * Otherwise use a4c location name as Yorc location name.
 */
@Component(value = YorcLocationModifier.YORC_LOCATION_MODIFIER_TAG)
public class YorcLocationModifier extends TopologyModifierSupport {
    public static final String YORC_LOCATION_MODIFIER_TAG = "yorc-location-modifier";

    protected static final String YORC_LOCATION_METAPROP_NAME = "YORC_LOCATION";
    protected static final String YORC_LOCATION_TAG_NAME = "location";

    @Resource
    protected MetaPropertiesService metaPropertiesService;

    @Override
    public void process(Topology topology, FlowExecutionContext context) {
        String yorcLocation = getProvidedMetaproperty(context, YORC_LOCATION_METAPROP_NAME);
        if (yorcLocation != null && !"".equals(yorcLocation)) {
            setYorcLocation(topology, yorcLocation);
            return;
        }

        Location location = getLocation(context);
        if (location!= null) {
            setYorcLocation(topology, location.getName());
        }

    }

    private void setYorcLocation(Topology topology, String yorcLocation) {
        safe(topology.getNodeTemplates()).values().forEach(node -> {
            setNodeTagValue(node, YORC_LOCATION_TAG_NAME, yorcLocation);
        });
    }

    private Location getLocation(FlowExecutionContext context) {
        Object deploymentLocations = context.getExecutionCache()
                .get(FlowExecutionContext.DEPLOYMENT_LOCATIONS_MAP_CACHE_KEY);
        if (deploymentLocations != null && deploymentLocations instanceof Map) {
            Map<String, Location> locations = (Map<String, Location>) deploymentLocations;
            Optional<Location> l = locations.values().stream().findFirst();
            if (l.isPresent()) {
                return l.get();
            }
        }
        return null;
    }

    /**
     * Search for a meta-property value in location.
     *
     * @param context          Execution context that allows modifiers to access
     *                         some useful contextual information
     * @param metaPropertyName Name of the metaproperty
     * @return the value of a meta-property.
     */
    protected String getProvidedMetaproperty(FlowExecutionContext context, String metaPropertyName) {
        String locationMetaPropertyKey = this.metaPropertiesService.getMetapropertykeyByName(metaPropertyName, MetaPropertyTarget.LOCATION);
        // first, get the namespace using the value of a meta property on application
        String providedProperty = null;
        // if defined, use the the value of a meta property of the targeted location
        if (locationMetaPropertyKey != null) {
            Location location = getLocation(context);
            if (location != null) {
                String locationProvidedProperty = safe(location.getMetaProperties()).get(locationMetaPropertyKey);
                if (StringUtils.isNotEmpty(locationProvidedProperty)) {
                    providedProperty = locationProvidedProperty;
                }
            }

        }
        return providedProperty;
    }
}