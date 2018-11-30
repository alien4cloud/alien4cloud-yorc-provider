package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.statemachine.action.Action;

import alien4cloud.paas.yorc.context.tasks.DeployTask;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentActions {

	public static Action<DeploymentStates, DeploymentMessages> buildZip() {
		return stateContext -> {
			DeployTask task = (DeployTask) stateContext.getMessageHeaders().get("task");
			if (log.isDebugEnabled()) {
				log.debug(String.format("Action: DeployTask of %s is starting.", task.getInfo().getContext().getDeploymentId()));
			}
			task.doStart();
		};
	}
}
