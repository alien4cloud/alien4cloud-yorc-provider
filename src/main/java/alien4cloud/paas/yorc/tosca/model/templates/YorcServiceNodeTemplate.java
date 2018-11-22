package alien4cloud.paas.yorc.tosca.model.templates;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;

/**
 * A {@link ServiceNodeTemplate} providing a substitutable directive to the
 * orchestrator, so that the Orchestrator knows it has to substitute this node
 * template as described in <a href="http://docs.oasis-open.org/tosca/TOSCA-Simple-Profile-YAML/v1.2/TOSCA-Simple-Profile-YAML-v1.2.html">TOSCA specification</a>
 * section 2.10.2 "Definition of the top-level service template".
 */
@Getter
@Setter
@NoArgsConstructor
public class YorcServiceNodeTemplate extends ServiceNodeTemplate {
    /**
     * Directive to the orchestrator specifying that this Node Template is not
     * defined here and will have to be substituted by a Node Template defined
     * elsewhere.
     */
    public static final String DIRECTIVE_SUBSTITUTABLE = "substitutable";

    private String[] directives;

    public YorcServiceNodeTemplate(ServiceNodeTemplate serviceNodeTemplate) {
        super(serviceNodeTemplate, serviceNodeTemplate.getAttributeValues());
        this.directives = new String[]{DIRECTIVE_SUBSTITUTABLE};
    }
}
