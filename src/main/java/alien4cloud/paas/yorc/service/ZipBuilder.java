package alien4cloud.paas.yorc.service;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.model.components.CSARSource;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.tosca.model.templates.YorcServiceNodeTemplate;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.*;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.repository.CsarFileRepository;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static com.google.common.io.Files.copy;


@Slf4j
@Component
public class ZipBuilder {

    // Supported Locations
    private static final int LOC_OPENSTACK = 1;
    private static final int LOC_KUBERNETES = 2;
    private static final int LOC_AWS = 3;
    private static final int LOC_SLURM = 4;

    @Inject
    private ICSARRepositorySearchService csarRepoSearchService;

    @Inject
    private CsarFileRepository fileRepository;

    @Inject
    private ToscaTopologyExporter toscaTopologyExporter;

    @Inject
    private ToscaComponentExporter toscaComponentExporter;

    @Resource(name = "yorc-tosca-parser")
    private ToscaParser parser;

    /**
     * Create the zip for yorc, with a modified yaml and all needed archives.
     * Assumes a file original.yml exists in the current directory
     * @throws IOException
     */
    public byte[] build(PaaSTopologyDeploymentContext context) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            context.getDeploymentTopology().getDependencies().forEach(d -> {
                if (!"tosca-normative-types".equals(d.getName())) {
                    Csar csar = csarRepoSearchService.getArchive(d.getName(), d.getVersion());
                    final String importSource = csar.getImportSource();
                    // importSource is null when this is a reference to a Service
                    // provided by another deployment
                    if (importSource == null || CSARSource.ORCHESTRATOR != CSARSource.valueOf(importSource)) {
                        try {
                            csar2zip(zos, csar);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            for (Map.Entry<String, NodeTemplate> nodeTemplateEntry :  context.getDeploymentTopology().getNodeTemplates().entrySet()) {
                if (nodeTemplateEntry.getValue() instanceof ServiceNodeTemplate) {
                    // Define a service node with a directive to the orchestrator
                    // that this Node Template is substitutable
                    YorcServiceNodeTemplate yorcServiceNodeTemplate = new YorcServiceNodeTemplate((ServiceNodeTemplate)nodeTemplateEntry.getValue());
                    nodeTemplateEntry.setValue(yorcServiceNodeTemplate);
                }
            }

            Map<String,String> artifactMap = processArtifacts(context,zos);

            // Copy modified topology
            createZipEntries("topology.yml", zos);

            // Get the yaml of the application as built by from a4c
            DeploymentTopology dtopo = context.getDeploymentTopology();
            Csar myCsar = new Csar(context.getDeploymentPaaSId(), dtopo.getArchiveVersion());
            myCsar.setToscaDefinitionsVersion(ToscaParser.LATEST_DSL);
            String yaml = toscaTopologyExporter.getYaml(myCsar, dtopo, true, artifactMap);
            zos.write(yaml.getBytes(Charset.forName("UTF-8")));
            zos.closeEntry();
        }

        return bos.toByteArray();
    }

    /**
     * Get csar and add entries in zip file for it
     * @return relative path to the yml, ex: welcome-types/3.0-SNAPSHOT/welcome-types.yaml
     */
    private String csar2zip(ZipOutputStream zos, Csar csar) throws IOException, ParsingException {
        // Get path directory to the needed info:
        // should be something like: ...../runtime/csar/<module>/<version>/expanded
        // We should have a yml or a yaml here
        Path csarPath = fileRepository.getExpandedCSAR(csar.getName(), csar.getVersion());
        String dirname = csarPath.toString();
        File directory = new File(dirname);
        String relative = csar.getName() + "/" + csar.getVersion() + "/";
        String ret = relative + csar.getYamlFilePath();
        // All files under this directory must be put in the zip
        URI base = directory.toURI();
        Deque<File> queue = new LinkedList<>();
        queue.push(directory);
        while (!queue.isEmpty()) {
            directory = queue.pop();
            for (File kid : directory.listFiles()) {
                String name = base.relativize(kid.toURI()).getPath();
                if (kid.isDirectory()) {
                    queue.push(kid);
                } else {
                    File file = kid;
                    createZipEntries(relative + name, zos);
                    if (name.equals(csar.getYamlFilePath())) {
                        ToscaContext.Context oldCtx = ToscaContext.get();
                        ParsingResult<ArchiveRoot> parsingResult;
                        try {
                            ToscaContext.init(Sets.newHashSet());
                            parsingResult = parser.parseFile(Paths.get(file.getAbsolutePath()));
                        } finally {
                            ToscaContext.set(oldCtx);
                        }
                        if (parsingResult.getContext().getParsingErrors().size() > 0) {
                            Boolean hasFatalError = false;
                            for (ParsingError error :
                                    parsingResult.getContext().getParsingErrors()) {
                                if (error.getErrorLevel().equals(ParsingErrorLevel.ERROR)) {
                                    log.error(error.toString());
                                    hasFatalError = true;
                                } else {
                                    log.warn(error.toString());
                                }
                            }
                            if (hasFatalError) {
                                continue;
                            }
                        }
                        ArchiveRoot root = parsingResult.getResult();
                        String yaml;
                        if (root.hasToscaTopologyTemplate()) {
                            log.debug("File has topology template : " + name);
                            yaml = toscaTopologyExporter.getYaml(csar, root.getTopology(), false);
                        } else {
                            yaml = toscaComponentExporter.getYaml(root);
                        }

                        zos.write(yaml.getBytes(Charset.forName("UTF-8")));
                    } else {
                        copy(file, zos);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Add all ZipEntry for this file path
     * If path is a directory, it must be ended by a "/".
     * All directory entries must be ended by a "/", and all simple file entries must be not.
     * TODO use this method everywhere
     * @param fullpath
     * @param zos
     */
    private void createZipEntries(String fullpath, ZipOutputStream zos) throws IOException {
        log.debug("createZipEntries for " + fullpath);
        int index = 0;
        String name = "";
        while (name.length() < fullpath.length()) {
            index = fullpath.indexOf("/", index) + 1;
            if (index <= 1) {
                name = fullpath;
            } else {
                name = fullpath.substring(0, index);
            }
            try {
                zos.putNextEntry(new ZipEntry(name));
                log.debug("new ZipEntry: " + name);
            } catch (ZipException e) {
                if (e.getMessage().contains("duplicate")) {
                    //log.debug("ZipEntry already added: " + name);
                } else {
                    log.error("Cannot add ZipEntry: " + name, e);
                    throw e;
                }
            }
        }
    }

    /**
     * Process artifacts
     */
    private Map<String,String> processArtifacts(PaaSTopologyDeploymentContext context,ZipOutputStream zos) throws IOException {
        Map<String,String> map = Maps.newHashMap();

        PaaSTopology topology = context.getPaaSTopology();

        for (PaaSNodeTemplate node : topology.getAllNodes().values()) {
            Map<String,DeploymentArtifact> artifacts = node.getTemplate().getArtifacts();
            if (artifacts == null) continue;

            for (DeploymentArtifact artifact : artifacts.values() ) {
                // Skip local component stored in components
                if (artifact.getArtifactRepository() == null) continue;

                if (!map.containsKey(artifact.getArtifactPath())) {
                    String targetName = doCopyArtifact(artifact,zos);
                    map.put(artifact.getArtifactPath(), targetName);
                }
            }
        }

        for (Map.Entry<String,String> entry : map.entrySet()) {
            log.info("Artifact Copy: {} -> {}", entry.getKey(), entry.getValue());
        }

        return map;
    }

    private String doCopyArtifact(DeploymentArtifact artifact,ZipOutputStream zos) throws IOException {
        String targetName = UUID.randomUUID().toString();
        Path artifactPath = Paths.get(artifact.getArtifactPath());

        if (artifactPath.toFile().isDirectory()) {
            targetName += "/";

            createZipEntries(targetName,zos);
            for (String file : artifactPath.toFile().list()) {
                Path filePath = artifactPath.resolve(file);
                recursivelyCopyArtifact(filePath, targetName + file,zos);
            }
        } else {
            createZipEntries(targetName,zos);
            copy(artifactPath.toFile(),zos);
        }

        return targetName;
    }

    private void recursivelyCopyArtifact(Path path, String baseTargetName, ZipOutputStream zos) throws IOException {
        if (path.toFile().isDirectory()) {
            String folderName = baseTargetName + "/";
            createZipEntries(folderName, zos);
            for (String file : path.toFile().list()) {
                Path filePath = path.resolve(file);
                recursivelyCopyArtifact(filePath, folderName + file, zos);
            }
        } else {
            createZipEntries(baseTargetName, zos);
            copy(path.toFile(), zos);
        }
    }

}
