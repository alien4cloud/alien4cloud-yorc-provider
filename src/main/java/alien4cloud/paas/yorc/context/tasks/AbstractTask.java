package alien4cloud.paas.yorc.context.tasks;

import lombok.AccessLevel;
import lombok.Getter;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;

public abstract class AbstractTask {

    @Inject
    @Getter(AccessLevel.PROTECTED)
    protected ScheduledExecutorService executorService;
}
