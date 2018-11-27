package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.event.ConfigurationUpdatedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
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
    private AsyncClientHttpRequestFactory factory;

    // Template
    private AsyncRestTemplate restTemplate;

    @EventListener
    private void onConfigurationUpdated(ConfigurationUpdatedEvent event) {
        yorcUrl = event.getConfiguration().getUrlYorc();

        factory = factoryBuilder.build();

        cutomizeFactory(factory);

        restTemplate = new AsyncRestTemplate(factory);
    }

    protected void cutomizeFactory(AsyncClientHttpRequestFactory factory) {
    }

    public <T> ListenableFuture<ResponseEntity<T>> sendRequest(String url, HttpMethod method, Class<T> responseType, HttpEntity entity) {
        log.debug("Yorc Request({},{}",method,url);
        if (entity.getHeaders() != null) {
            log.debug("Headers: {}",entity.getHeaders());
        }

        return restTemplate.exchange(url,method,entity,responseType);
    }
}
