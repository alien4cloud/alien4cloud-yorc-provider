package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.event.ConfigurationUpdatedEvent;
import io.reactivex.Single;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;

import javax.inject.Inject;
import java.util.concurrent.ExecutionException;

@Slf4j
public abstract class AbstractClient {

    @Inject
    AsyncClientHttpRequestFactoryBuilder factoryBuilder;

    // Yorc URL
    @Getter
    private String yorcUrl;

    // Factory
    private Netty4ClientHttpRequestFactory factory;

    // Template
    private AsyncRestTemplate restTemplate;

    @EventListener
    private void onConfigurationUpdated(ConfigurationUpdatedEvent event) {
        yorcUrl = event.getConfiguration().getUrlYorc();

        factory = factoryBuilder.build();

        customizeFactory(factory);

        restTemplate = new AsyncRestTemplate(factory);
    }

    protected void customizeFactory(Netty4ClientHttpRequestFactory factory) {
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

        ListenableFuture<ResponseEntity<T>> future = restTemplate.exchange(url,method,entity,responseType);

        return fromFuture(restTemplate.exchange(url,method,entity,responseType))
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
