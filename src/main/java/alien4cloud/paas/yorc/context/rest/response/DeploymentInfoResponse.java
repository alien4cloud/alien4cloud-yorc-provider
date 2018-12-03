package alien4cloud.paas.yorc.context.rest.response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@ToString
@Getter
@Setter
public class DeploymentInfoResponse {
    private List<Link> links;
    private String status;
    private String id;
}
