package alien4cloud.paas.yorc.context.rest.response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@ToString
@Getter
@Setter
public class PurgeDTO {

    private List<Error> errors;
}
