package alien4cloud.paas.yorc.context.rest.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TaskDTO {

    private String id;

    @JsonProperty("target_id")
    private String targetId;

    private String type;

    private String status;

    @JsonProperty("error_message")
    private String errorMessage;
}
