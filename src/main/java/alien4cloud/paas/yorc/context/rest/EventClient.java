package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.EventDTO;
import alien4cloud.paas.yorc.util.RestUtil;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventClient extends AbstractClient {

    public Single<ResponseEntity<EventDTO>> get(int index) {
        String url = getYorcUrl() + "/events?index=" + index;
        return sendRequest(url, HttpMethod.GET, EventDTO.class, buildHttpEntityWithDefaultHeader());
    }

    public Single<Integer> getLastIndex() {
        String url = getYorcUrl() + "/events";

        return sendRequest(url,HttpMethod.GET,String.class,buildHttpEntityWithDefaultHeader())
            .map(RestUtil.extractHeader("X-yorc-Index"))
            .map(Integer::parseInt);
    }
}
