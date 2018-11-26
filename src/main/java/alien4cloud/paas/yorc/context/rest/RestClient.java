package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

@Slf4j
public class RestClient {

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 60000;

    @Inject
    private EventLoopGroup eventLoopGroup;

    // Factory
    private Netty4ClientHttpRequestFactory factory;

    // Template
    private AsyncRestTemplate restTemplate;

    // Yorc URL
    private String yorcUrl;

    public void setConfiguration(ProviderConfiguration providerConfiguration) throws PluginConfigurationException {
        // Yorc URL
        yorcUrl = providerConfiguration.getUrlYorc();

        // Create the factory
        factory = new Netty4ClientHttpRequestFactory(eventLoopGroup);

        // TODO: Sysprop this
        factory.setReadTimeout(READ_TIMEOUT);
        factory.setConnectTimeout(CONNECTION_TIMEOUT);

        try {
            if (Boolean.TRUE.equals(providerConfiguration.getInsecureTLS())) {
                SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                factory.setSslContext(sslContext);
            }
        } catch(SSLException e) {
            throw new PluginConfigurationException("Fail to configure plugin",e);
        }

        restTemplate = new AsyncRestTemplate(factory);
    }

    public <T> ListenableFuture<ResponseEntity<T>> sendRequest(String url,HttpMethod method,Class<T> responseType,HttpEntity entity) {
        log.debug("Yorc Request({},{}",method,url);
        if (entity.getHeaders() != null) {
            log.debug("Headers: {}",entity.getHeaders());
        }

        return restTemplate.exchange(url,method,entity,responseType);
    }

    public ListenableFuture<ResponseEntity<String>> sendTopologyToYorc(String deploymentId,byte[] bytes) {
        String url = yorcUrl + "/deployments/" + deploymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        return sendRequest(url,HttpMethod.PUT,String.class,entity);
    }
}
