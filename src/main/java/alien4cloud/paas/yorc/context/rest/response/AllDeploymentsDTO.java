package alien4cloud.paas.yorc.context.rest.response;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class AllDeploymentsDTO {

    private List<DeploymentDTO> deployments = Lists.newArrayList();
}
