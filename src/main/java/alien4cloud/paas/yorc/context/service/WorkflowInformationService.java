package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.rest.response.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkflowInformationService {

    public void onEvent(Event event) {
        log.info("WFIS EVENT: {}",event);
    }
}
