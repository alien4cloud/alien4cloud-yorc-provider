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
    public static final String EVT_INSTANCE     = "Instance";
    public static final String EVT_CUSTOMCMD     = "CustomCommand";
    public static final String EVT_DEPLOYMENT   = "Deployment";
    public static final String EVT_SCALING      = "scaling";
    public static final String EVT_WORKFLOW     = "Workflow";
    public static final String EVT_WORKFLOWSTEP = "WorkflowStep";
    public static final String EVT_ALIENTASK    = "AlienTask";

    private String timestamp;
    private String deploymentId;
    private String status;
    private String type;
    private String workflowId;
    private String alienExecutionId;
    private String nodeId;
    private String instanceId;
    private String operationName;
    private String alienTaskId;
    private String targetNodeId;
    private String targetInstanceId;
    private String stepId;
}
