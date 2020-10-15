package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
public abstract class AbstractClient {

    @Inject
    TemplateManager manager;

    @Resource
    private ProviderConfiguration configuration;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @return Yorc Url from configuration
     */
    protected String getYorcUrl() {
        return configuration.getUrlYorc();
    }

    /**
     * This allows to build an HTTPEntity object with body and default headers with JSON ACCEPT
     * @param body
     * @return HttpEntity
     */
    protected final HttpEntity<String> buildHttpEntityWithDefaultHeader(Map<String,Object> body) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity(mapper.writeValueAsString(body),headers);
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
        if (log.isTraceEnabled()) {
            log.trace("Yorc Request({},{}",method,url);

            if (entity.getHeaders() != null) {
                log.trace("Headers: {}",entity.getHeaders());
            }
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
