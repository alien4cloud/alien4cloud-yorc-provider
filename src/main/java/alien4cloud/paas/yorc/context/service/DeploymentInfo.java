package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class DeploymentInfo {

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private DeploymentStatus status;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private PaaSTopologyDeploymentContext context;
}
