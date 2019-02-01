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
    public static final String EVT_OPERATION    = "custom-command";
    public static final String EVT_DEPLOYMENT   = "Deployment";
    public static final String EVT_SCALING      = "scaling";
    public static final String EVT_WORKFLOW     = "Workflow";
    public static final String EVT_WORKFLOWSTEP = "WorkflowStep";
    public static final String EVT_ALIENTASK    = "AlienTask";
    public static final String EVT_ATTRIBUTE    = "AttributeValue";

    private String timestamp;
    private String nodeId;
    private String instanceId;
    private String status;
    private String type;
    private String taskId;
    private String deploymentId;
    private String workflowId;
    private String alienExecutionId;
    private String alienTaskId;
    private String stepId;
    private String operationName;
    private String attribute;
    private String value;
}
