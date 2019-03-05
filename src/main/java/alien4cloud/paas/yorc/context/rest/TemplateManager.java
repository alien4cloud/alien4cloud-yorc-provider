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
import org.apache.http.pool.PoolStats;
import org.apache.http.ssl.SSLContexts;

import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.naming.SelfNaming;
import org.springframework.stereotype.Service;
import org.springframework.web.client.AsyncRestTemplate;


import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.util.Hashtable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@ManagedResource
public class TemplateManager implements SelfNaming {

    @Inject
    private Scheduler scheduler;

    @Inject
    private ConnectingIOReactor reactor;

    @Resource
    private ProviderConfiguration configuration;

    private PoolingNHttpClientConnectionManager manager;

    @Resource(name = "http-thread-factory")
    private ThreadFactory threadFactory;

    // Template
    private AsyncRestTemplate template;


    private AtomicBoolean running = new AtomicBoolean(true);

    private Disposable disposable;

    @PostConstruct
    public void configure() throws PluginConfigurationException {
        log.info("Configuring connection manager using ConnectionMaxPoolSize: {}, ConnectionTtl: {}s", configuration.getConnectionMaxPoolSize(), configuration.getConnectionTtl());
        log.info("Connection eviction will be done each {} seconds, ConnectionMaxIdleTime: {}s", configuration.getConnectionEvictionPeriod(), configuration.getConnectionMaxIdleTime());

        AsyncClientHttpRequestFactory factory;
        HostnameVerifier verifier;

        HttpHost proxy = getProxy();

        SSLContext context = SSLContexts.createSystemDefault();

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
                configuration.getConnectionTtl(),
                TimeUnit.SECONDS
            );
        manager.setDefaultMaxPerRoute(configuration.getConnectionMaxPoolSize());
        manager.setMaxTotal(configuration.getConnectionMaxPoolSize());

        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                    .setConnectionManager(manager)
                    .setThreadFactory(threadFactory);

        if (proxy != null) {
            log.info("Will use HTTP proxy {}", proxy);
            builder = builder.setProxy(proxy);
        }

        CloseableHttpAsyncClient httpClient = builder.build();

        factory = new HttpComponentsAsyncClientHttpRequestFactory(httpClient);

        template = new AsyncRestTemplate(factory);

        // Schedule eviction task
        disposable = Completable.timer(configuration.getConnectionEvictionPeriod(), TimeUnit.SECONDS, scheduler).subscribe(this::evictionTask);
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
        if (log.isDebugEnabled()) {
            log.debug("Before connection eviction : {}", manager.getTotalStats());
        }
        manager.closeExpiredConnections();
        manager.closeIdleConnections(configuration.getConnectionMaxIdleTime(),TimeUnit.SECONDS);
        if (log.isDebugEnabled()) {
            log.debug("After connection eviction : {}", manager.getTotalStats());
        }

        // Reschedule eviction task
        disposable = Completable.timer(configuration.getConnectionEvictionPeriod(), TimeUnit.SECONDS, scheduler).subscribe(this::evictionTask);
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

    @ManagedAttribute
    public int getAvailable() {
        return manager.getTotalStats().getAvailable();
    }

    @ManagedAttribute
    public int getMax() {
        return manager.getTotalStats().getMax();
    }

    @ManagedAttribute
    public int getLeased() {
        return manager.getTotalStats().getLeased();
    }

    @ManagedAttribute
    public int getPending() {
        return manager.getTotalStats().getPending();
    }

    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException {
        Hashtable<String,String> kv = new Hashtable();
        kv.put("type","Orchestrators");
        kv.put("orchestratorName",configuration.getOrchestratorName());
        kv.put("name","TemplateManager");
        return new ObjectName("alien4cloud.paas.yorc",kv);
    }
}
