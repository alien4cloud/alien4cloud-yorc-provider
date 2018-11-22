package alien4cloud.paas.yorc.context.tasks;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.model.components.CSARSource;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.IPaaSCallback;

import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.service.DeploymentInfo;
import alien4cloud.paas.yorc.context.service.DeploymentService;
import alien4cloud.paas.yorc.service.ToscaComponentExporter;
import alien4cloud.paas.yorc.service.ToscaTopologyExporter;
import alien4cloud.paas.yorc.service.ZipBuilder;
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
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.inject.Inject;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static com.google.common.io.Files.copy;

@Slf4j
@Component
@Scope("prototype")
public class DeployTask extends AbstractTask {

    @Inject
    private DeploymentService deploymentService;

    @Inject
    private ZipBuilder zipBuilder;

    private DeploymentInfo info;

    private IPaaSCallback<?> callback;

    /**
     * Start the deploy task.
     *
     * Called from UI Thread
     *
     * @param context
     * @param callback
     */
    public void start(PaaSTopologyDeploymentContext context, IPaaSCallback<?> callback) {
        info = deploymentService.createDeployment(context);

        this.callback = callback;

        getExecutorService().submit(this::doStart);
    }

    /**
     * Starting the deployment on task pool
     */
    private void doStart() {
        log.debug("Deploying " + info.getContext().getDeploymentPaaSId() + " with id : " + info.getContext().getDeploymentId());

        try {
            zipBuilder.build(info.getContext());
        } catch(IOException e) {
            // TODO: Deal with exception
            log.error("NOT HANDLED: {}",e);
        }
    }

}
