package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import com.google.common.collect.Maps;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;

@Component
public class DeploymentService {

    @Inject
    private ApplicationContext context;

    private final Map<String,DeploymentInfo> map = Maps.newConcurrentMap();

    public DeploymentInfo createDeployment(PaaSTopologyDeploymentContext deploymentContext) {
        return createDeployment(deploymentContext,DeploymentStatus.INIT_DEPLOYMENT);
    }

    public DeploymentInfo createDeployment(PaaSTopologyDeploymentContext deploymentContext,DeploymentStatus initialStatus) {
        DeploymentInfo info = (DeploymentInfo) context.getBean(DeploymentInfo.class);

        info.setContext(deploymentContext);
        info.setStatus(initialStatus);

        map.put(deploymentContext.getDeploymentPaaSId(),info);

        return info;
    }

    public DeploymentInfo getDeployment(String paasId) {
        return map.get(paasId);
    }
}
