package alien4cloud.paas.yorc.context.rest;


import alien4cloud.paas.yorc.util.FutureUtil;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TaskClient extends AbstractClient {

    /**
     * Stop a task.
     */
    public ListenableFuture<String> stopTask(String taskUrl) {
        String url = getYorcUrl() + taskUrl;

        ListenableFuture<ResponseEntity<String>> f = FutureUtil.convert(sendRequest(url,HttpMethod.DELETE,String.class,buildHttpEntityWithDefaultHeader()));
        return Futures.transform(f,(Function<ResponseEntity<String>,String>) this::extractLocation);
    }
}
