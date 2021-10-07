package alien4cloud.paas.yorc.context.service.fsm;

public enum FsmStates {
	UNDEPLOYED,
	DEPLOYMENT_INIT,
	UNKOWN,
	DEPLOYMENT_IN_PROGRESS,
	DEPLOYED,
	CANCELLATION_REQUESTED,
	TASK_CANCELLING,
	UNDEPLOYMENT_IN_PROGRESS,
	UNDEPLOYMENT_PURGING,
	FAILED,
	PRE_UPDATE_IN_PROGRESS,
	UPDATE_IN_PROGRESS,
	POST_UPDATE_IN_PROGRESS,
	UPDATED,
	UPDATE_FAILED,
	UNDEPLOYMENT_FAILED,
	UNDEPLOYMENT_WAITING_LOGS,
	UNDEPLOYMENT_WAITING_EVENTS,
	PURGE_FAILED,
	RESUME_DEPLOY,
	RESUME_UNDEPLOY
}
