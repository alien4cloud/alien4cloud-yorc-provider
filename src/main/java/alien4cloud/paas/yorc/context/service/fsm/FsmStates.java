package alien4cloud.paas.yorc.context.service.fsm;

public enum FsmStates {
	UNDEPLOYED, DEPLOYMENT_INIT, DEPLOYMENT_IN_PROGRESS, DEPLOYED, UNDEPLOYMENT_IN_PROGRESS, FAILED
}
