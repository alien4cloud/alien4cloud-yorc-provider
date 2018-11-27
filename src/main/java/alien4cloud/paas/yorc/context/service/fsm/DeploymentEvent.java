package alien4cloud.paas.yorc.context.service.fsm;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
}
