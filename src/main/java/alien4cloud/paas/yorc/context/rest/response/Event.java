package alien4cloud.paas.yorc.context.rest.response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class Event {

    /**
     *  Possible values for Yorc event types
     *  Check with Yorc code for these values.
     */
    public static final String EVT_INSTANCE   = "instance";
    public static final String EVT_OPERATION  = "custom-command";
    public static final String EVT_DEPLOYMENT = "deployment";
    public static final String EVT_SCALING    = "scaling";
    public static final String EVT_WORKFLOW   = "workflow";

    private String timestamp;
    private String node;
    private String instance;
    private String status;
    private String type;
    private String task_id;
    private String deployment_id;
}
