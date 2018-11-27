package alien4cloud.paas.yorc.context.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
public class EventService {

    @Inject
    protected ScheduledExecutorService executorService;

    private int index;

    public void init() {
        // Bootstrap the service on task executor
        executorService.submit(this::queryEvents);
    }

    private void queryEvents() {
        log.debug("get events - index={}",index);
    }
}
