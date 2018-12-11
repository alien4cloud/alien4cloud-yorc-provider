package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.ssl.SSLContexts;

import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.AsyncRestTemplate;


import javax.annotation.Resource;
import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.concurrent.ThreadFactory;

@Slf4j
@Service
public class TemplateManager {

    @Inject
    private ConnectingIOReactor reactor;

    @Resource(name = "http-thread-factory")
    private ThreadFactory threadFactory;

    // Template
    private AsyncRestTemplate template;

    @Getter
    private ProviderConfiguration configuration;

    public void configure(ProviderConfiguration configuration) throws PluginConfigurationException {
        AsyncClientHttpRequestFactory factory;
        HostnameVerifier verifier;

        SSLContext context = SSLContexts.createSystemDefault();

        this.configuration = configuration;

        if (Boolean.TRUE.equals(configuration.getInsecureTLS())) {
            verifier = new NoopHostnameVerifier();
        } else {
            verifier = new DefaultHostnameVerifier();
        }

        Registry<SchemeIOSessionStrategy> registry = RegistryBuilder.<SchemeIOSessionStrategy>create()
                .register("http" , NoopIOSessionStrategy.INSTANCE)
                .register( "https", new SSLIOSessionStrategy(context,verifier))
                .build();

        PoolingNHttpClientConnectionManager manager = new PoolingNHttpClientConnectionManager(reactor,registry);
        manager.setDefaultMaxPerRoute(20);
        manager.setMaxTotal(20);

        CloseableHttpAsyncClient httpClient = HttpAsyncClients.custom()
                .setConnectionManager(manager)
                .setThreadFactory(threadFactory)
                .build();

        factory =  new HttpComponentsAsyncClientHttpRequestFactory(httpClient);

        template = new AsyncRestTemplate(factory);
    }

    public AsyncRestTemplate get() {
        return template;
    }

}
