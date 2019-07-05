package alien4cloud.paas.yorc.service;

import alien4cloud.tosca.serializer.ToscaPropertySerializerUtils;
import alien4cloud.tosca.serializer.ToscaSerializerUtils;
import org.alien4cloud.tosca.model.definitions.AbstractArtifact;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArtifactUtils {

    private final Map<String,String> artifactMap;

    private static final Pattern GET_INPUT_ARTIFACT_PATTERN = Pattern.compile("\\{ *get_input_artifact: +[^}]+}");

    public ArtifactUtils(Map<String,String> artifactMap) {
        this.artifactMap = artifactMap;
    }

    public Map<String, DeploymentArtifact> getTopologyArtifacts(String topologyArchiveName, String topologyArchiveVersion, Map<String, DeploymentArtifact> artifacts) {
        if (artifacts == null) {
            return Collections.emptyMap();
        }

        // Only generate artifacts that are really stored inside the topology
        return artifacts.entrySet().stream().filter(
                artifact -> (topologyArchiveName.equals(artifact.getValue().getArchiveName()) && topologyArchiveVersion.equals(artifact.getValue().getArchiveVersion()))
                          || artifact.getValue().getArtifactRepository() != null
                          || (artifact.getValue().getArtifactRef() != null && GET_INPUT_ARTIFACT_PATTERN.matcher(artifact.getValue().getArtifactRef()).matches())
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String formatArtifact(AbstractArtifact value, int indent) {

        String spaces = ToscaPropertySerializerUtils.indent(indent);

        if (value.getArtifactRepository() != null) {
            StringBuilder buffer = new StringBuilder();

            buffer.append(spaces).append(String.format("# Repo: %s Ref: %s\n",value.getArtifactRepository(),value.getArtifactRef()));
            buffer.append(spaces).append("file: ").append(artifactMap.get(value.getArtifactPath())).append("\n");
            buffer.append(spaces).append("type: ").append(value.getArtifactType()).append("\n");

            if (buffer.length() > 1) {
                buffer.setLength(buffer.length() - 1);
            }

            return buffer.toString();
        } else {
            return ToscaSerializerUtils.formatArtifact(value,indent);
        }
    }

}
