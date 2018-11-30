package alien4cloud.paas.yorc.context.service.fsm;

public enum DeploymentStates {
	UNDEPLOYED, DEPLOYMENT_INIT, DEPLOYMENT_SUBMITTED, DEPLOYMENT_IN_PROGRESS, DEPLOYED, UNDEPLOYMENT_IN_PROGRESS, FAILED
}
