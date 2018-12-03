package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.event.ConfigurationUpdatedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

import javax.inject.Inject;

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

    public <T> ListenableFuture<ResponseEntity<T>> sendRequest(String url, HttpMethod method, Class<T> responseType, HttpEntity entity) {
        if (log.isDebugEnabled()) {
            logRequest(url, method, responseType, entity);
        }

        return restTemplate.exchange(url,method,entity,responseType);
    }

    protected final <T> String extractLocation(ResponseEntity<T> entity) {
        return entity.getHeaders().getFirst("Location");
    }
}
