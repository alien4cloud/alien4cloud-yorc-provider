package alien4cloud.paas.yorc.context.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.deployment.DeploymentLoggingService;
import alien4cloud.paas.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.List;

@Slf4j
@Service
public class LogEventService {

    @Inject
    private DeploymentRegistry registry;

    @Inject
    private BusService bus;

    @Inject
    private DeploymentLoggingService loggingService;

    public void onEvent(List<PaaSDeploymentLog> events) {
        if (events.size()>0) {
            if (log.isDebugEnabled()) {
                log.debug("Flushing {} log events",events.size());
            }
            save(events.toArray(new PaaSDeploymentLog[0]));
        }
    }

    public void save(PaaSDeploymentLog event) {
        loggingService.save(event);
    }

    public void save(PaaSDeploymentLog[] events) {
        loggingService.save(events);
    }

    @PostConstruct
    public void init() {
        bus.subscribeLogs(this::onEvent);
    }
}
