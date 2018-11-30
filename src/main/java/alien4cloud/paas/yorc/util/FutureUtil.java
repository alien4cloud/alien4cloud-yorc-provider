package alien4cloud.paas.yorc.util;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class FutureUtil {

    private FutureUtil() {
    }

    static public <T> ListenableFuture<T> convert(org.springframework.util.concurrent.ListenableFuture<T> sf) {
        return new ListenableFuture<T>() {
            @Override
            public void addListener(Runnable runnable, Executor executor) {
                sf.addCallback(new ListenableFutureCallback<T>() {
                    @Override
                    public void onFailure(Throwable throwable) {
                        executor.execute(runnable);
                    }

                    @Override
                    public void onSuccess(T t) {
                        executor.execute(runnable);
                    }
                });
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return sf.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return sf.isCancelled();
            }

            @Override
            public boolean isDone() {
                return sf.isDone();
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                return sf.get();
            }

            @Override
            public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return sf.get(timeout,unit);
            }
        };
    }

    public static <T> ListenableFuture<T> unwrap(org.springframework.util.concurrent.ListenableFuture<ResponseEntity<T>> sf) {
        ListenableFuture<ResponseEntity<T>> gf = convert(sf);
        return Futures.transform(gf, (Function<ResponseEntity<T>,T>) HttpEntity::getBody);
    }


}
