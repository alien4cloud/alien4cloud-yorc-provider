import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.yorc.context.service.fsm.DeploymentEvent;
import alien4cloud.paas.yorc.context.service.fsm.DeploymentMessages;
import alien4cloud.paas.yorc.context.service.fsm.StateMachineService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { FSMTestConfiguration.class })
public class StateMachineTest {

	@Autowired
	private StateMachineService smService;

	@Test
	public void test_normal_deploy_and_then_undeploy() {
//		String id = "id1";
//		smService.sendEvent(new DeploymentEvent(id, DeploymentMessages.DEPLOYMENT_STARTED));
//		Assert.assertEquals(DeploymentStatus.INIT_DEPLOYMENT, smService.getState(id));
//		smService.sendEvent(new DeploymentEvent(id, DeploymentMessages.DEPLOYMENT_IN_PROGRESS));
//		Assert.assertEquals(DeploymentStatus.DEPLOYMENT_IN_PROGRESS, smService.getState(id));
//		smService.sendEvent(new DeploymentEvent(id, DeploymentMessages.DEPLOYMENT_SUCCESS));
//		Assert.assertEquals(DeploymentStatus.DEPLOYED, smService.getState(id));
//		smService.sendEvent(new DeploymentEvent(id, DeploymentMessages.UNDEPLOYMENT_STARTED));
//		Assert.assertEquals(DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS, smService.getState(id));
//		smService.sendEvent(new DeploymentEvent(id, DeploymentMessages.UNDEPLOYMENT_SUCCESS));
//		Assert.assertEquals(DeploymentStatus.UNDEPLOYED, smService.getState(id));
	}
}
