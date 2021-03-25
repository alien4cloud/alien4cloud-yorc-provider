package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.util.RestUtil;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServerClient extends AbstractClient {

    /**
     * Get the server version
     *
     * @return
     */
    public Single<String> getVersion() {
        String url = getYorcUrl() + "/server/info";

        return sendRequest(url, HttpMethod.GET,String.class, buildHttpEntityWithDefaultHeader())
                .map(HttpEntity::getBody)
                .map(RestUtil.toJson())
                .map(RestUtil.jsonAsText("yorc_version"));
    }
}
