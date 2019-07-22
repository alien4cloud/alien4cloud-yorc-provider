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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.KeyManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.Certificate;
import java.security.cert.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Hashtable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;

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
        SSLContext context;

        HttpHost proxy = getProxy();

        if(!isSSLConfigProvided()) {
            context = SSLContexts.createSystemDefault();
        } else {
            // In order to configure the SSLContext we need to create a keystore
            KeyStore keystore;
            try {
                keystore = createKeystore();
            } catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException | InvalidKeySpecException e) {
                e.printStackTrace();
                throw new PluginConfigurationException("Failed to create keystore", e);
            }
            try {
                context = SSLContext.getInstance("TLS");
                context.init(getKeyManagers(keystore), getTrustManagers(keystore), null);
            } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException
                    | KeyManagementException e) {
                e.printStackTrace();
                throw new PluginConfigurationException("Failed to create SSL context", e);
            }
        }

        if (Boolean.TRUE.equals(configuration.getInsecureTLS())) {
            verifier = new NoopHostnameVerifier();
            // TODO
            // In the Yorc plugin code, here a custom SSLContext is created with SSLContexts.custom()
            // Then a SSLContextFactory is created with a new AllowAllHostnameVerifier()
            // Should we use AllowAllHostnameVerifier even if we didn't create a custom SSLContext ?
            // verifier = new AllowAllHostnameVerifier() -- ??
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

    private boolean isSSLConfigProvided() {
        if(configuration.getUrlYorc().startsWith("https")) {
            // If SSL configuration is not provided in the plugin, relying
            // on system default keystore and truststore
            String caCertif = configuration.getCaCertificate();
            String clientCertif = configuration.getClientCertificate();
            String clientKey = configuration.getClientKey();
            if ((caCertif == null || caCertif.isEmpty())
                    || (clientCertif == null || clientCertif.isEmpty())
                    || (clientKey == null || clientKey.isEmpty())
                    ) {
                log.warn("Missing CA|Client certificate|Client key in plugin configuration, will use system defaults");
                if (System.getProperty("javax.net.ssl.keyStore") == null || System.getProperty("javax.net.ssl.keyStorePassword") == null) {
                    log.warn("Using SSL but you didn't provide client keystore and password. This means that if required by Yorc client authentication will fail.\n" +
                            "Please use -Djavax.net.ssl.keyStore <keyStorePath> -Djavax.net.ssl.keyStorePassword <password> while starting java VM");
                }
                if (System.getProperty("javax.net.ssl.trustStore") == null || System.getProperty("javax.net.ssl.trustStorePassword") == null) {
                    log.warn("You didn't provide client trustore and password. Using defalut one \n" +
                            "Please use -Djavax.net.ssl.trustStore <trustStorePath> -Djavax.net.ssl.trustStorePassword <password> while starting java VM");
                }
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private KeyStore createKeystore() throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException {
        // Create the CA certificate from its configuration string value
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                configuration.getCaCertificate().getBytes());
        X509Certificate trustedCert = (X509Certificate)certFactory.generateCertificate(inputStream);
        inputStream.close();

        // Create the client private key from its configuration string value
        String keyContent = configuration.getClientKey().
                replaceFirst("-----BEGIN PRIVATE KEY-----\n", "").
                replaceFirst("\n-----END PRIVATE KEY-----", "").trim();
        PKCS8EncodedKeySpec clientKeySpec = new PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(keyContent));
        // Getting the key algorithm
        ASN1InputStream bIn = new ASN1InputStream(new ByteArrayInputStream(clientKeySpec.getEncoded()));
        PrivateKeyInfo pki = PrivateKeyInfo.getInstance(bIn.readObject());
        bIn.close();
        String algorithm = pki.getPrivateKeyAlgorithm().getAlgorithm().getId();
        // Workaround for a missing algorithm OID in the list of default providers
        if ("1.2.840.113549.1.1.1".equals(algorithm)) {
            algorithm = "RSA";
        }
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        PrivateKey clientKey = keyFactory.generatePrivate(clientKeySpec);

        // Create the client certificate from its configuration string value
        inputStream = new ByteArrayInputStream(
                configuration.getClientCertificate().getBytes());
        java.security.cert.Certificate clientCert = certFactory.generateCertificate(inputStream);
        inputStream.close();

        // Create an empty keystore
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null);

        // Add the certificate authority
        keystore.setCertificateEntry(
                trustedCert.getSubjectX500Principal().getName(),
                trustedCert);

        // Add client key/certificate and chain to the Key store
        java.security.cert.Certificate[] chain = {clientCert, trustedCert};
        keystore.setKeyEntry("Yorc Client", clientKey, "yorc".toCharArray(), chain);
        return keystore;
    }

    private KeyManager[] getKeyManagers(KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(keystore, "yorc".toCharArray());
        return kmf.getKeyManagers();
    }

    private TrustManager[] getTrustManagers(KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keystore);
        return tmf.getTrustManagers();
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
