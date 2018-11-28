package alien4cloud.paas.yorc.context.service.fsm;

/**
 * Input events to the state machine
 */
public enum DeploymentMessages {
	DEPLOYMENT_STARTED,
	DEPLOYMENT_IN_PROGRESS,
	DEPLOYMENT_SUCCESS,
	UNDEPLOYMENT_STARTED,
	UNDEPLOYMENT_SUCCESS,
	FAILURE,
}
