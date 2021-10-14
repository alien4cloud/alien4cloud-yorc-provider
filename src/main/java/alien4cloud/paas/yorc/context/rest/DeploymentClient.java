package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.PurgeDTO;
import alien4cloud.paas.yorc.context.rest.response.TaskDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.yorc.context.rest.response.AllDeploymentsDTO;
import alien4cloud.paas.yorc.context.rest.response.DeploymentDTO;
import alien4cloud.paas.yorc.util.RestUtil;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Component
public class DeploymentClient extends AbstractClient {

    /**
     * Send the topology to Yorc
     *
     * @param deploymentId
     * @param bytes zip file as bytes
     * @param isUpdate if true perform an update on an existing deployment, otherwise submit a new one
     * @return
     */
    public Single<ResponseEntity<String>> sendTopology(String deploymentId, byte[] bytes, boolean isUpdate) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
        HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);

        HttpMethod method = HttpMethod.PUT;
        if (isUpdate) {
            method = HttpMethod.PATCH;
        }

        return sendRequest(url, method, String.class, entity);
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

    public Single<DeploymentDTO> get(String deploymentId) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;
        return sendRequest(url,HttpMethod.GET,DeploymentDTO.class, buildHttpEntityWithDefaultHeader()).map(HttpEntity::getBody);
    }

    public Observable<DeploymentDTO> get() {
        String url = getYorcUrl() + "/deployments";
        return sendRequest(url,HttpMethod.GET,AllDeploymentsDTO.class, buildHttpEntityWithDefaultHeader())
                .map(RestUtil.extractBodyWithDefault(AllDeploymentsDTO::new))
                .toObservable()
                .flatMapIterable(AllDeploymentsDTO::getDeployments);
    }

    public Single<String> purge(String deploymentId,boolean stopOnError) {
        return undeploy(deploymentId,stopOnError,true);
    }

    public Single<String> undeploy(String deploymentId,boolean stopOnError) {
        return undeploy(deploymentId,stopOnError,false);
    }

    public Single<String> undeploy(String deploymentId, boolean stopOnError, boolean purge) {
        String url = getYorcUrl() + "/deployments/" + deploymentId;

        if (purge && stopOnError) {
            url += "?purge&stopOnError";
        } else if (purge) {
            url += "?purge";
        } else if (stopOnError) {
            url += "?stopOnError";
        }

        return sendRequest(url, HttpMethod.DELETE, String.class, buildHttpEntityWithDefaultHeader())
            .map(RestUtil.extractHeader("Location"));
    }

    public Single<PurgeDTO> syncPurge(String deploymentId, boolean force) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/purge";

        if (force) {
            url += "?force";
        }


        return sendRequest(url, HttpMethod.POST, PurgeDTO.class, buildHttpEntityWithDefaultHeader()).map(HttpEntity::getBody);
    }

    public Single<TaskDTO> getTask(String taskUrl) {
        String url = getYorcUrl() + taskUrl;
        return sendRequest(url, HttpMethod.GET, TaskDTO.class, buildHttpEntityWithDefaultHeader()).map(HttpEntity::getBody);
    }

    public Completable resumeTask(String taskUrl) {
        String url = getYorcUrl() + taskUrl;
        return sendRequest(url,HttpMethod.PUT,String.class,buildHttpEntityWithDefaultHeader()).ignoreElement();
    }

    public Completable resetStep(String deploymentId, String executionId,String stepId, boolean done) {
        try {
            String url = String.format("%s/deployments/%s/tasks/%s/steps/%s",getYorcUrl(),deploymentId,executionId,stepId);

            Map<String,Object> request = Maps.newHashMap();
            if (done) {
                request.put("status","DONE");
            } else {
                request.put("status","INITIAL");
            }

            return sendRequest(url,HttpMethod.PUT,String.class,buildHttpEntityWithDefaultHeader(request)).ignoreElement();
        } catch(JsonProcessingException e) {
            return Completable.error(e);
        }
    }

    public Completable cancelTask(String deploymentId, String taskId) {
        String taskUrl = String.format("/deployments/%s/tasks/%s",deploymentId,taskId);
        return cancelTask(taskUrl);
    }

    public Completable cancelTask(String taskUrl) {
        String url = getYorcUrl() + taskUrl;
        return sendRequest(url,HttpMethod.DELETE,String.class,buildHttpEntityWithDefaultHeader()).ignoreElement();
    }

    public <T> Observable<T> queryUrl(String url,Class<T> clazz) {
        return sendRequest(getYorcUrl() + url, HttpMethod.GET, clazz,buildHttpEntityWithDefaultHeader())
                .map(HttpEntity::getBody)
                .toObservable();
    }

    public Single<String> executeWorkflow(String deploymentId, String workflowName, Map<String,Object> inputs, boolean continueOnError) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/workflows/" + workflowName;
        if (continueOnError) {
            url += "?continueOnError";
        }

        try {
            Map<String,Object> request = Maps.newHashMap();
            request.put("inputs",inputs);

            return sendRequest(url, HttpMethod.POST, String.class, buildHttpEntityWithDefaultHeader(request))
                    .map(RestUtil.extractHeader("Location"));
        } catch(JsonProcessingException e) {
            return Single.error(e);
        }
    }

    public Single<String> scale(String deploymentId,String nodeName,int delta) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/scale/" + nodeName + "?delta=" + delta;

        return sendRequest(url, HttpMethod.POST, String.class, buildHttpEntityWithDefaultHeader())
                .map(RestUtil.extractHeader("Location"));
    }

    public Single<String> executeOperation(String deploymentId, NodeOperationExecRequest request) {
        String url = getYorcUrl() + "/deployments/" + deploymentId + "/custom";

        JSONObject json = new JSONObject();
        json.put("node",request.getNodeTemplateName());
        json.put("interface",request.getInterfaceName());
        json.put("name",request.getOperationName());
        json.put("inputs",request.getParameters());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE,MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<byte[]> entity = new HttpEntity<>(json.toString().getBytes(),headers);

        return sendRequest(url, HttpMethod.POST,String.class,entity)
                .map(RestUtil.extractHeader("Location"));
    }

}
