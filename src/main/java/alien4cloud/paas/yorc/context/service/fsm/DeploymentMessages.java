package alien4cloud.paas.yorc.context.service.fsm;

public enum DeploymentMessages {
	DEPLOYMENT_STARTED,
	DEPLOYMENT_IN_PROGRESS,
	DEPLOYMENT_SUCCESS,
	UNDEPLOYMENT_STARTED,
	UNDEPLOYMENT_SUCCESS,
	FAILURE,
}
