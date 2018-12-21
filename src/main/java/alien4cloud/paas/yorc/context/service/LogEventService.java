package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.yorc.context.rest.response.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogEventService {

    public void onEvent(LogEvent event) {
        log.info("LOG: {}",event);
    }
}
