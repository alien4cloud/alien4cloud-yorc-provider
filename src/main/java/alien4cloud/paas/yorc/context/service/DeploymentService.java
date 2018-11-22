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
        DeploymentInfo info = (DeploymentInfo) context.getBean(DeploymentInfo.class);

        info.setContext(deploymentContext);
        info.setStatus(DeploymentStatus.INIT_DEPLOYMENT);

        map.put(deploymentContext.getDeploymentPaaSId(),info);

        return info;
    }

}
