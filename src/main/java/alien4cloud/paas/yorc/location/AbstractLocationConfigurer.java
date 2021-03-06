package alien4cloud.paas.yorc.location;

import alien4cloud.deployment.matching.services.nodes.MatchingConfigurations;
import alien4cloud.deployment.matching.services.nodes.MatchingConfigurationsParser;
import alien4cloud.model.common.Tag;
import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.deployment.matching.MatchingFilterDefinition;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.exception.PluginParseException;
import alien4cloud.paas.yorc.service.PluginArchiveService;
import alien4cloud.plugin.PluginManager;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.AlienConstants;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.ArchiveParser;
import org.alien4cloud.tosca.model.definitions.constraints.*;
import org.alien4cloud.tosca.model.types.AbstractToscaType;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * Configure resources for the location type.
 */
@Slf4j
@Component
public abstract class AbstractLocationConfigurer implements ILocationConfiguratorPlugin {

    /** This is used to tag types provided by any yorc location */
    public static final String YORC_LOCATION_DEFINED_TYPE_TAG = "_yorc_location_defined_type_";

    @Inject
    protected ArchiveParser archiveParser;

    @Inject
    protected MatchingConfigurationsParser matchingConfigurationsParser;

    @Inject
    protected PluginManager pluginManager;

    @Inject
    protected ManagedPlugin selfContext;

    @Inject
    protected LocationResourceGeneratorService resourceGeneratorService;

    @Inject
    private PluginArchiveService archiveService;

    protected List<PluginArchive> archives;

    @Override
    public List<PluginArchive> pluginArchives() throws PluginParseException {
        if (this.archives == null) {
            parseLocationArchives(getLocationArchivePaths());
        }
        return this.archives;
    }

    protected void addToAchive(List<PluginArchive> archives, String path) throws ParsingException {
        Path archivePath = selfContext.getPluginPath().resolve(path);
        // Parse the archives
        ParsingResult<ArchiveRoot> result = archiveParser.parseDir(archivePath, AlienConstants.GLOBAL_WORKSPACE_ID);
        PluginArchive pluginArchive = new PluginArchive(result.getResult(), archivePath);
        archives.add(pluginArchive);
    }

    public List<String> getAllResourcesTypes() {
        List<String> resourcesTypes = Lists.newArrayList();
        for (PluginArchive pluginArchive : this.pluginArchives()) {
            for (String nodeType : pluginArchive.getArchive().getNodeTypes().keySet()) {
                resourcesTypes.add(nodeType);
            }
        }
        return resourcesTypes;
    }

    private void parseLocationArchives(String[] paths) {
        this.archives = Lists.newArrayList();
        for (String path : paths) {
            log.debug("Parse Location Archive " + path);
            this.archives.add(archiveService.parsePluginArchives(path));
        }
        archives.forEach(this::decorateArchiveContents);
    }

    private void decorateArchiveContents(PluginArchive pluginArchive) {
        safe(pluginArchive.getArchive().getArtifactTypes()).forEach(this::decorateTOSCAType);
        safe(pluginArchive.getArchive().getCapabilityTypes()).forEach(this::decorateTOSCAType);
        safe(pluginArchive.getArchive().getDataTypes()).forEach(this::decorateTOSCAType);
        safe(pluginArchive.getArchive().getNodeTypes()).forEach(this::decorateTOSCAType);
        safe(pluginArchive.getArchive().getPolicyTypes()).forEach(this::decorateTOSCAType);
        safe(pluginArchive.getArchive().getRelationshipTypes()).forEach(this::decorateTOSCAType);
    }

    private <T extends AbstractToscaType> void decorateTOSCAType(String typeName, T type) {
        if (type.getTags() == null) {
            type.setTags(new ArrayList<>());
        }
        type.getTags().add(new Tag(AbstractLocationConfigurer.YORC_LOCATION_DEFINED_TYPE_TAG, "_internal_"));
    }


    public Map<String, MatchingConfiguration> getMatchingConfigurations(String matchingConfigRelativePath) {
        Path matchingConfigPath = selfContext.getPluginPath().resolve(matchingConfigRelativePath);
        MatchingConfigurations matchingConfigurations = null;
        try {
            matchingConfigurations = matchingConfigurationsParser.parseFile(matchingConfigPath).getResult();
        } catch (ParsingException e) {
            return Maps.newHashMap();
        }
        Map<String, MatchingConfiguration> ret = matchingConfigurations.getMatchingConfigurations();
        if (ret == null) {
            return Maps.newHashMap();
        }
        return ret;
    }

    protected abstract String[] getLocationArchivePaths();
}
