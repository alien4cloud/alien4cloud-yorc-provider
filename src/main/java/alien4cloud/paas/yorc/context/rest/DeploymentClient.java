package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.DeploymentInfoResponse;
import alien4cloud.paas.yorc.util.FutureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class DeploymentClient extends AbstractClient {

    /**
     * Send the topology to Yorc
     *
     * @param deploymentId
     * @param bytes zip file as bytes
     * @return
     */
    public ListenableFuture<ResponseEntity<String>> sendTopology(String deploymentId, byte[] bytes) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        return FutureUtil.convert(sendRequest(url,HttpMethod.PUT,String.class,entity));
    }

    /**
     * Scale the topology.
     *
     * @param deploymentId
     * @param nodeName
     * @param delta
     * @return
     */
    public ListenableFuture<ResponseEntity<String>> scaleTopology(String deploymentId,String nodeName,int delta) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/scale/" + nodeName + "?delta=" + delta;

        return FutureUtil.convert(sendRequest(url,HttpMethod.POST,String.class, buildHttpEntityWithDefaultHeader()));
    }

    /**
     * Get the status of a topology.
     *
     * @param deploymentId
     * @return
     */
    public ListenableFuture<String> getStatus(String deploymentId) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        ListenableFuture<String> f = FutureUtil.unwrap(sendRequest(url,HttpMethod.GET,String.class, buildHttpEntityWithDefaultHeader()));

        return Futures.transform(f,(Function<String,String>) this::extractStatus);
    }

    public ListenableFuture<DeploymentInfoResponse> getInfos(String deploymentId) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;
        return FutureUtil.unwrap(sendRequest(url,HttpMethod.GET,DeploymentInfoResponse.class, buildHttpEntityWithDefaultHeader()));
    }

    @SneakyThrows
    private String extractStatus(String json) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode node = root.path("status");

        return node.asText();
    }
}
