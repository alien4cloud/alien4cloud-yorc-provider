package alien4cloud.paas.yorc.service;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.model.components.CSARSource;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.tosca.model.templates.YorcServiceNodeTemplate;
import alien4cloud.paas.yorc.util.ShowTopology;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.*;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.repository.CsarFileRepository;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.Operation;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
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
    public void build(PaaSTopologyDeploymentContext context) throws IOException {
        Location loc = context.getLocations().get("_A4C_ALL");

        final int location = getLocation(loc);

        final File zip = new File("topology.zip");
        final OutputStream bos= new FileOutputStream(zip);
        //final OutputStream bos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            context.getDeploymentTopology().getDependencies().forEach(d -> {
                if (!"tosca-normative-types".equals(d.getName())) {
                    Csar csar = csarRepoSearchService.getArchive(d.getName(), d.getVersion());
                    final String importSource = csar.getImportSource();
                    // importSource is null when this is a reference to a Service
                    // provided by another deployment
                    if (importSource == null || CSARSource.ORCHESTRATOR != CSARSource.valueOf(importSource)) {
                        try {
                            csar2zip(zos, csar, location);
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

            // Copy overwritten artifacts for each node
            PaaSTopology ptopo = context.getPaaSTopology();
            for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
                copyArtifacts(node, zos);
            }

            // Copy modified topology
            createZipEntries("topology.yml", zos);

            // Get the yaml of the application as built by from a4c
            DeploymentTopology dtopo = context.getDeploymentTopology();
            Csar myCsar = new Csar(context.getDeploymentPaaSId(), dtopo.getArchiveVersion());
            myCsar.setToscaDefinitionsVersion(ToscaParser.LATEST_DSL);
            String yaml = toscaTopologyExporter.getYaml(myCsar, dtopo, true);
            zos.write(yaml.getBytes(Charset.forName("UTF-8")));
            zos.closeEntry();
        }
    }

    private int getLocation(Location loc) {
        // Check location
        int location = LOC_OPENSTACK;

        for (CSARDependency dep : loc.getDependencies()) {
            if (dep.getName().contains("kubernetes")) {
                location = LOC_KUBERNETES;
                break;
            }
            if (dep.getName().contains("slurm")) {
                location = LOC_SLURM;
                break;
            }
            if (dep.getName().contains("aws")) {
                location = LOC_AWS;
                break;
            }
        }

        return location;
    }

    private void matchKubernetesImplementation(ArchiveRoot root) {
        root.getNodeTypes().forEach((k, t) -> {
            Map<String, Interface> interfaces = t.getInterfaces();
            if (interfaces != null) {
                Interface ifce = interfaces.get("tosca.interfaces.node.lifecycle.Standard");
                if (ifce != null) {
                    Operation start = ifce.getOperations().get("start");
                    if (start != null && start.getImplementationArtifact() != null) {
                        String implArtifactType = start.getImplementationArtifact().getArtifactType();
                        // Check implementation artifact type Not null to avoid NPE
                        if (implArtifactType != null) {
                            if (implArtifactType.equals("tosca.artifacts.Deployment.Image.Container.Docker")) {
                                start.getImplementationArtifact().setArtifactType("tosca.artifacts.Deployment.Image.Container.Docker.Kubernetes");
                            }
                        } //else {
                        //System.out.println("Found start implementation artifcat with type NULL : " + start.getImplementationArtifact().toString());
                        // The implementation artifact with type null was :
                        // ImplementationArtifact{} AbstractArtifact{artifactType='null', artifactRef='scripts/kubectl_endpoint_start.sh', artifactRepository='null', archiveName='null', archiveVersion='null', repositoryURL='null', repositoryName='null', artifactPath=null}
                        //}
                    }
                }
            }
        });
    }

    private void matchKubernetesImplementation(Map<String, Object> topology) {
        Map<String, HashMap> nodeTypes = ((Map) topology.get("node_types"));

        nodeTypes.forEach((k,nodeType)->{
            String t = (String) getNestedValue(nodeType, "interfaces.Standard.start.implementation.type");
            if (t.equals("tosca.artifacts.Deployment.Image.Container.Docker")) {
                setNestedValue(nodeType, "interfaces.Standard.start.implementation.type", "tosca.artifacts.Deployment.Image.Container.Docker.Kubernetes");
            }
        });
    }

    /**
     * Get csar and add entries in zip file for it
     * @return relative path to the yml, ex: welcome-types/3.0-SNAPSHOT/welcome-types.yaml
     */
    private String csar2zip(ZipOutputStream zos, Csar csar, int location) throws IOException, ParsingException {
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
                        if (location == LOC_KUBERNETES) {
                            matchKubernetesImplementation(root);
                        }

                        String yaml;
                        if (root.hasToscaTopologyTemplate()) {
                            log.debug("File has topology template : " + name);
                            yaml = toscaTopologyExporter.getYaml(csar, root.getTopology(), false);
                        } else {
                            yaml = toscaComponentExporter.getYaml(root);
                        }

                        zos.write(yaml.getBytes(Charset.forName("UTF-8")));
                    } else{
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
     * Copy artifacts to archive
     * @param node
     * @param zout
     */
    private void copyArtifacts(PaaSNodeTemplate node, ZipOutputStream zout) {
        String name = node.getId();

        // Check if this component has artifacts
        Map<String, DeploymentArtifact> map = node.getTemplate().getArtifacts();
        if (map == null) {
            log.debug("Component with no artifact: " + name);
            return;
        }

        // Process each artifact
        for (Map.Entry<String, DeploymentArtifact> da : map.entrySet()) {
            String aname =  name + "/" + da.getKey();
            DeploymentArtifact artifact = da.getValue();
            String artRepo = artifact.getArtifactRepository();
            if (artRepo == null) {
                continue;
            }
            ShowTopology.printArtifact(artifact);
            if  (artRepo.equals(ArtifactRepositoryConstants.ALIEN_TOPOLOGY_REPOSITORY)) {
                // Copy artifact from topology repository to the root of archive.
                String from = artifact.getArtifactPath();
                log.debug("Copying local artifact: " + aname + " path=" + from);
                Path artifactPath = Paths.get(from);
                try {
                    String filename = artifact.getArtifactRef();
                    createZipEntries(filename, zout);
                    copy(artifactPath.toFile(), zout);
                } catch (Exception e) {
                    log.error("Could not copy local artifact " + aname, e);
                }
            } else {
                // Copy remote artifact
                String from = artifact.getArtifactPath();
                log.debug("Copying remote artifact: " + aname + " path=" + from);
                Path artifactPath = Paths.get(from);
                try {
                    String filename = artifact.getArtifactRef();
                    createZipEntries(filename, zout);
                    copy(artifactPath.toFile(), zout);
                } catch (Exception e) {
                    log.error("Could not copy remote artifact " + aname, e);
                }
                // Workaround for a bug in a4c: artifact not added in topology.yml
                // TODO Remove this when a4c bug SUPALIEN-926 is fixed.
                addRemoteArtifactInTopology(name, da.getKey(), artifact);
            }
        }
    }

    /**
     * Workaround for a4c issue: SUPALIEN-926
     * TODO Remove this when a4c bug is fixed. (planned for 1.5)
     * @param node Node Name
     * @param key Name of the artifact
     * @param da
     */
    private void addRemoteArtifactInTopology(String node, String key, DeploymentArtifact da) {
        log.debug("");
        String oldFileName = "topology.yml";
        String tmpFileName = "tmp_topology.yml";

        log.debug("Add remote artifact in topology (workaround for SUPALIEN-926)");
        log.debug(node + " " + key + " : " + da.getArtifactRef() + " - " + da.getArtifactType());

        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(tmpFileName));
            br = new BufferedReader(new FileReader(oldFileName));
            String line;
            boolean inNode = false;
            boolean done = false;
            while ((line = br.readLine()) != null) {
                if (! done) {
                    if (line.startsWith("    " + node + ":")) {
                        inNode = true;
                        bw.append(line).append("\n");
                        continue;
                    }
                    if (! inNode) {
                        bw.append(line).append("\n");
                        continue;
                    }
                    if (! line.startsWith("      ")) {
                        bw.append("      artifacts:\n");
                        // Add here the 3 lines to describe the remote artifact
                        String l1 = "        " + key + ":\n";
                        String l2 = "          file: " + da.getArtifactRef() + "\n";
                        String l3 = "          type: " + da.getArtifactType() + "\n";
                        bw.append(l1).append(l2).append(l3);
                        done = true;
                        bw.append(line).append("\n");
                        continue;
                    }
                    if (line.startsWith("      artifacts:")) {
                        bw.append(line).append("\n");
                        // Add here the 3 lines to describe the remote artifact
                        String l1 = "        " + key + ":\n";
                        String l2 = "          file: " + da.getArtifactRef() + "\n";
                        String l3 = "          type: " + da.getArtifactType() + "\n";
                        bw.append(l1).append(l2).append(l3);
                        done = true;
                        continue;
                    }
                }
                bw.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("Error while modifying topology.yml");
            return;
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException e) {
                log.error("Error closing " + oldFileName, e);
            }
            try {
                if (bw != null)
                    bw.close();
            } catch (IOException e) {
                log.error("Error closing " + tmpFileName, e);
            }
        }
        // Once everything is complete, delete old file..
        File oldFile = new File(oldFileName);
        oldFile.delete();

        // And rename tmp file's name to old file name
        File newFile = new File(tmpFileName);
        newFile.renameTo(oldFile);
    }

    private void setNestedValue(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split(".");
        Object v = map;
        for (int i = 0; i < parts.length-1; i++) {
            v = ((Map<String, Object>)v).get(parts[i]);
        }
        if (v != null) {
            ((Map<String, Object>)v).put(parts[parts.length-1], value);
        }
    }

    private Object getNestedValue(Map<String, Object> map, String path) {
        String[] parts = path.split(".");
        Object v = map;
        for (String s : parts) {
            if (v == null) return null;
            v = ((Map<String, Object>)v).get(s);
        }
        return v;
    }
}
