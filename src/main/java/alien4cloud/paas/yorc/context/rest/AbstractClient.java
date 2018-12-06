package alien4cloud.paas.yorc.context.rest;

import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;

@Slf4j
public abstract class AbstractClient {

    @Inject
    TemplateManager manager;

    /**
     * @return Yorc Url from configuration
     */
    protected String getYorcUrl() {
        return manager.getConfiguration().getUrlYorc();
    }

    protected <T> void logRequest(String url, HttpMethod method, Class<T> responseType, HttpEntity entity) {
        log.debug("Yorc Request({},{}",method,url);
        if (entity.getHeaders() != null) {
            log.debug("Headers: {}",entity.getHeaders());
        }
    }

    /**
     * This allows to build an HTTPEntity object with body and default headers with JSON ACCEPT
     * @param body
     * @return HttpEntity
     */
    protected final <T> HttpEntity<T> buildHttpEntityWithDefaultHeader(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(body, headers);
    }

    /**
     * This allows to build an HTTPEntity object with default headers with JSON ACCEPT
     * @return HttpEntity
     */
    protected final  HttpEntity buildHttpEntityWithDefaultHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(headers);
    }

    public <T> Single<ResponseEntity<T>> sendRequest(String url, HttpMethod method, Class<T> responseType, HttpEntity entity) {
        if (log.isDebugEnabled()) {
            logRequest(url, method, responseType, entity);
        }

        return fromFuture(manager.get().exchange(url,method,entity,responseType))
                .onErrorResumeNext( throwable -> {
                    if (throwable instanceof ExecutionException) {
                        // Unwrap exception
                        throwable = throwable.getCause();
                    }
                    return Single.error(throwable);
                });
    }


    protected final <T> Single<T> fromFuture(ListenableFuture<T> future) {
        return Single.defer(() ->
                Single.create(source -> {
                    future.addCallback(new ListenableFutureCallback<T>() {
                        @Override
                        public void onFailure(Throwable throwable) {
                            source.onError(throwable);
                        }

                        @Override
                        public void onSuccess(T t) {
                            source.onSuccess(t);
                        }
                    });
                })
        );
    }
}
