package alien4cloud.paas.yorc.context.service;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.paas.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

@Slf4j
@Service
public class LogEventService {

    @Inject
    private DeploymentRegistry registry;

    @Inject
    private BusService bus;

    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO dao;

    public void onEvent(PaaSDeploymentLog event) {
        save(event);
    }

    public void save(PaaSDeploymentLog event) {
        dao.save(event);
    }

    @PostConstruct
    public void init() {
        bus.subscribeLogs(this::onEvent);
    }
}
