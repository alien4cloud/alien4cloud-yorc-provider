package alien4cloud.paas.yorc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.functions.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

public class RestUtil {

    private RestUtil() {
    }

    public static boolean isHttpError(Throwable throwable,HttpStatus code) {
        if (throwable instanceof HttpClientErrorException) {
            HttpClientErrorException httpErrorException = (HttpClientErrorException) throwable;
            if (httpErrorException.getStatusCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static Function<String,JsonNode> toJson() {
        final ObjectMapper mapper = new ObjectMapper();
        return (String value) -> {
            return mapper.readTree(value);
        };
    }

    public static Function<JsonNode,String> jsonAsText(final String path) {
        return (JsonNode node) -> {
            return node.path(path).asText();
        };
    }

    public static <T> Function<ResponseEntity<T>,String> extractHeader(String name) {
        return (entity) -> entity.getHeaders().getFirst(name);
    }
}
