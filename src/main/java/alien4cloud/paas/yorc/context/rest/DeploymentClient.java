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
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

@Slf4j
@Component
public class DeploymentClient extends AbstractClient {

    public ListenableFuture<ResponseEntity<String>> sendTopology(String deploymentId, byte[] bytes) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        return sendRequest(url,HttpMethod.PUT,String.class,entity);
    }

    public ListenableFuture<ResponseEntity<String>> scaleTopology(String deploymentId,String nodeName,int delta) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/scale/" + nodeName + "?delta=" + delta;

        return sendRequest(url,HttpMethod.POST,String.class, buildHttpEntityWithDefaultHeader());
    }

    public ListenableFuture<ResponseEntity<String>> getStatus(String deploymentId) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;
        return sendRequest(url,HttpMethod.GET,String.class, buildHttpEntityWithDefaultHeader());
    }

}
