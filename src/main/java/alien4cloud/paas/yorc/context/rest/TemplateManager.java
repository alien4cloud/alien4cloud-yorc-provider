package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TemplateManager {

    private static final int EVICTION_MAX_TIME = 5;
    private static final int EVICTION_MAX_IDLE = 2;

    private static final int EVITION_FREQUENCY = 1;

    @Inject
    private Scheduler scheduler;

    @Inject
    private ConnectingIOReactor reactor;

    private PoolingNHttpClientConnectionManager manager;

    @Resource(name = "http-thread-factory")
    private ThreadFactory threadFactory;

    // Template
    private AsyncRestTemplate template;


    private AtomicBoolean running = new AtomicBoolean(true);

    private Disposable disposable;

    @Getter
    private ProviderConfiguration configuration;

    public void configure(ProviderConfiguration configuration) throws PluginConfigurationException {
        AsyncClientHttpRequestFactory factory;
        HostnameVerifier verifier;

        HttpHost proxy = getProxy();

        SSLContext context = SSLContexts.createSystemDefault();

        this.configuration = configuration;

        if (Boolean.TRUE.equals(configuration.getInsecureTLS())) {
            verifier = new NoopHostnameVerifier();
        } else {
            verifier = new DefaultHostnameVerifier();
        }

        Registry<SchemeIOSessionStrategy> registry = RegistryBuilder.<SchemeIOSessionStrategy>create()
                .register("http", NoopIOSessionStrategy.INSTANCE)
                .register("https", new SSLIOSessionStrategy(context, verifier))
                .build();

        manager = new PoolingNHttpClientConnectionManager(
                reactor,
                null,
                registry,
                null,
                null,
                EVICTION_MAX_TIME,
                TimeUnit.MINUTES
            );
        manager.setDefaultMaxPerRoute(20);
        manager.setMaxTotal(20);

        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                    .setConnectionManager(manager)
                    .setThreadFactory(threadFactory);

        if (proxy != null) {
            builder = builder.setProxy(proxy);
        }

        CloseableHttpAsyncClient httpClient = builder.build();

        factory = new HttpComponentsAsyncClientHttpRequestFactory(httpClient);

        template = new AsyncRestTemplate(factory);

        // Schedule eviction task
        disposable = Completable.timer(EVITION_FREQUENCY, TimeUnit.MINUTES, scheduler).subscribe(this::evictionTask);
    }

    public AsyncRestTemplate get() {
        return template;
    }

    public void term() {
        running.set(false);

        if (disposable != null) {
            disposable.dispose();
        }
    }

    public void evictionTask() {
        if (running.get() == false) {
            return;
        }

        log.debug("YORC HTTP BEFORE EVICTION(avail={} , leased = {})",manager.getTotalStats().getAvailable(),manager.getTotalStats().getLeased());
        manager.closeExpiredConnections();
        manager.closeIdleConnections(EVICTION_MAX_IDLE,TimeUnit.MINUTES);
        log.debug("YORC HTTP AFTER  EVICTION(avail={} , leased = {})",manager.getTotalStats().getAvailable(),manager.getTotalStats().getLeased());

        // Reschedule eviction task
        disposable = Completable.timer(EVITION_FREQUENCY, TimeUnit.MINUTES, scheduler).subscribe(this::evictionTask);
    }

    private HttpHost getProxy() {
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");

        if (host == null) {
            return null;
        }

        if (port != null) {
            return new HttpHost(host, Integer.valueOf(port));
        } else {
            return new HttpHost(host, 8080);
        }
    }
}
