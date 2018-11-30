package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.context.annotation.Scope;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import alien4cloud.paas.yorc.context.tasks.AbstractTask;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Component
@Scope("prototype")
@AllArgsConstructor
public class DeploymentEvent {

	@Getter
	@Setter
	private String deploymentId;

	@Getter
	@Setter
	private DeploymentMessages message;

	@Getter
	@Setter
	private AbstractTask task;

	public MessageHeaders buildHeaders() {
		return task == null ? new MessageHeaders(Maps.newHashMap()) : new MessageHeaders(ImmutableMap.of("task", task));
	}

}
