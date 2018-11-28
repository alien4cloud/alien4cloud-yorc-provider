package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.paas.yorc.event.ConfigurationUpdatedEvent;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

@Slf4j
@Component
public class AsyncClientHttpRequestFactoryBuilder {

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 60000;

    @Inject
    private EventLoopGroup eventLoopGroup;

    @Inject
    private ApplicationEventPublisher publisher;

    private ProviderConfiguration configuration;

    private SslContext sslContext;

    Netty4ClientHttpRequestFactory build() {
        Netty4ClientHttpRequestFactory factory = new Netty4ClientHttpRequestFactory(eventLoopGroup);

        // TODO: Sysprop this
        factory.setReadTimeout(READ_TIMEOUT);
        factory.setConnectTimeout(CONNECTION_TIMEOUT);

        if (sslContext != null) {
            factory.setSslContext(sslContext);
        }

        return factory;
    }

    public void setConfiguration(ProviderConfiguration configuration) throws PluginConfigurationException {
            this.configuration = configuration;

        try {
            if (Boolean.TRUE.equals(configuration.getInsecureTLS())) {
                sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            }
        } catch(SSLException e) {
            throw new PluginConfigurationException("Fail to configure plugin",e);
        }

        publisher.publishEvent(new ConfigurationUpdatedEvent(this,configuration));
    }
}
