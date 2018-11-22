package alien4cloud.paas.yorc.service;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.serializer.VelocityUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@code ToscaComponentExporter} exports an {@link ArchiveRoot} into a YAML compatible with the Yorc engine DSL.
 *
 * @author Loic Albertin
 */
@Service("yorc-component-exporter-service")
@Slf4j
public class ToscaComponentExporter {

    /**
     * Get the yaml string out of a cloud service archive.
     *
     * @param archive the parsed archive.
     *
     * @return The TOSCA yaml file that describe the archive in Yorc format.
     */
    public String getYaml(ArchiveRoot archive) {
        Map<String, Object> velocityCtx = getVelocityContext();
        velocityCtx.put("archive", archive);
        ClassLoader oldctccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            StringWriter writer = new StringWriter();
            VelocityUtil.generate("alien4cloud/paas/yorc/tosca/types.yml.vm", writer, velocityCtx);
            return writer.toString();
        } catch (Exception e) {
            log.error("Exception while templating YAML for archive " + archive.getArchive().getName(), e);
            return ExceptionUtils.getFullStackTrace(e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldctccl);
        }
    }

    static Map<String, Object> getVelocityContext() {
        Map<String, Object> velocityCtx = new HashMap<>();
        velocityCtx.put("vtPath", "alien4cloud/paas/yorc/tosca");
        velocityCtx.put("yorcUtils", new ToscaComponentUtils());
        velocityCtx.put("stringsUtils", new StringUtils());
        return velocityCtx;
    }
}
