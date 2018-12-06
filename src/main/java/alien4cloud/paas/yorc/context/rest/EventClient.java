package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.EventResponse;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventClient extends AbstractClient {

    public Single<ResponseEntity<EventResponse>> getLogFromYorc(int index) {
        String url = getYorcUrl() + "/events?index=" + index;
        return sendRequest(url, HttpMethod.GET, EventResponse.class, buildHttpEntityWithDefaultHeader());
    }
}
