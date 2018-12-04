package alien4cloud.paas.yorc.context.tasks;

import alien4cloud.paas.yorc.observer.CallbackObserver;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import lombok.AccessLevel;
import lombok.Getter;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public abstract class AbstractTask {

    @Inject
    @Getter(AccessLevel.PROTECTED)
    protected ScheduledExecutorService executorService;

    @Inject
    protected Scheduler scheduler;

    protected <T> void subscribe(Single<T> single, Consumer<T> dataCallback, Consumer<Throwable> errorCallback) {
        single.subscribe(new CallbackObserver<>(dataCallback,errorCallback));
    }
}
