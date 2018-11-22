package alien4cloud.paas.yorc.service;

import alien4cloud.paas.yorc.tosca.model.templates.YorcServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.NodeTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ToscaExportUtils {
    public static Set<String> getDirectives(NodeTemplate nodeTemplate) {

        Set<String> directives = Collections.emptySet();

        if (nodeTemplate instanceof YorcServiceNodeTemplate) {
            YorcServiceNodeTemplate yorcServiceNodeTemplate = (YorcServiceNodeTemplate) nodeTemplate;
            if (yorcServiceNodeTemplate.getDirectives() != null) {
                directives = new HashSet<String>(Arrays.asList(yorcServiceNodeTemplate.getDirectives()));

            }
        }

        return directives;
    }
}
