package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.DeploymentInfoResponse;
import alien4cloud.paas.yorc.util.RestUtil;
import io.reactivex.Single;
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
    public Single<ResponseEntity<String>> sendTopology(String deploymentId, byte[] bytes) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        return sendRequest(url,HttpMethod.PUT,String.class,entity);
    }

    /**
     * Scale the topology.
     *
     * @param deploymentId
     * @param nodeName
     * @param delta
     * @return
     */
    public Single<ResponseEntity<String>> scaleTopology(String deploymentId,String nodeName,int delta) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/scale/" + nodeName + "?delta=" + delta;

        return sendRequest(url,HttpMethod.POST,String.class, buildHttpEntityWithDefaultHeader());
    }

    /**
     * Get the status of a topology.
     *
     * @param deploymentId
     * @return
     */
    public Single<String> getStatus(String deploymentId) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        return sendRequest(url,HttpMethod.GET,String.class, buildHttpEntityWithDefaultHeader())
                .map(HttpEntity::getBody)
                .map(RestUtil.toJson())
                .map(RestUtil.jsonAsText("status"));
    }

    public Single<DeploymentInfoResponse> getInfos(String deploymentId) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;
        return sendRequest(url,HttpMethod.GET,DeploymentInfoResponse.class, buildHttpEntityWithDefaultHeader()).map(HttpEntity::getBody);
    }

    public Single<String> undeploy(String deploymentId) {
        return undeploy(deploymentId,false);
    }

    public Single<String> undeploy(String deploymentId,boolean purge) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        if (purge == true) {
            url += "?purge";
        }

        return sendRequest(url,HttpMethod.DELETE,String.class,buildHttpEntityWithDefaultHeader())
            .map(RestUtil.extractHeader("Location"));
    }

    public Single<String> stopTask(String taskUrl) {
        String url = getYorcUrl() + taskUrl;

        return sendRequest(url,HttpMethod.DELETE,String.class,buildHttpEntityWithDefaultHeader())
            .map(RestUtil.extractHeader("Location"));
    }

}
