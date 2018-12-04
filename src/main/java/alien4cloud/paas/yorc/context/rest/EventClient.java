package alien4cloud.paas.yorc.context.rest;

import alien4cloud.paas.yorc.context.rest.response.EventResponse;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventClient extends AbstractClient {

    private static final int EXTENDED_READ_TIMEOUT = 900000;

    public Single<ResponseEntity<EventResponse>> getLogFromYorc(int index) {
        String url = getYorcUrl() + "/events?index=" + index;
        return sendRequest(url, HttpMethod.GET, EventResponse.class, buildHttpEntityWithDefaultHeader());
    }

    @Override
    protected void customizeFactory(Netty4ClientHttpRequestFactory factory) {
        // We override readTimeout because we use long polling
        factory.setReadTimeout(EXTENDED_READ_TIMEOUT);
    }

}
