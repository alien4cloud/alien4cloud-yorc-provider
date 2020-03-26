package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.LogEventDTO;
import alien4cloud.paas.yorc.util.RestUtil;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogEventClient extends AbstractClient {

    public Single<ResponseEntity<LogEventDTO>> get(long index) {
        String url = getYorcUrl() + "/logs?index=" + index;
        return sendRequest(url, HttpMethod.GET, LogEventDTO.class, buildHttpEntityWithDefaultHeader());
    }

    public Single<Long> getLastIndex() {
        String url = getYorcUrl() + "/logs";

        return sendRequest(url,HttpMethod.HEAD,String.class,buildHttpEntityWithDefaultHeader())
                .map(RestUtil.extractHeader("X-yorc-Index"))
                .map(Long::parseLong);
    }
}
