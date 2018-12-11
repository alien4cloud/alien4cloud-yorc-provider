package alien4cloud.paas.yorc.context.rest.response;

import alien4cloud.paas.yorc.context.rest.browser.BrowseableDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@ToString
@Getter
@Setter
public class DeploymentDTO implements BrowseableDTO {
    private List<Link> links;
    private String status;
    private String id;
}
