package alien4cloud.paas.yorc.modifier;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;

import static alien4cloud.utils.AlienUtils.safe;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.Topology;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Component("yorc-metapropscheck-modifier")
public class MetapropsCheckModifier extends TopologyModifierSupport {

    @Resource
    private MetaPropertiesService metaPropertiesService;

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            log.warn("Couldn't process MetapropsCheckModifier, got " + e.getMessage());
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {

       Map<String, String> metaProperties = safe(context.getEnvironmentContext().get().getApplication().getMetaProperties());

       safe(metaPropertiesService.getMetaPropConfigurationsByName(MetaPropertyTarget.APPLICATION)).forEach ((name, metaProp) -> {
          log.debug ("MetaProp [" + name + "] required " + metaProp.isRequired());
          if (metaProp.isRequired()) {
             String value = metaProperties.get(metaProp.getId());
             if ((value == null) || value.trim().equals("")) {
                log.error ("Application does not contain [" + name + "] metaProperty");
                context.getLog().error ("Metaproperty [" + name  + "] is not set !");
             } else {
                log.debug ("MetaProp [" + name + "] value [" + value + "]");
             }
          }
       });
    }
}
