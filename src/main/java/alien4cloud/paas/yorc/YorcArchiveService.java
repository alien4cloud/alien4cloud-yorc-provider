package alien4cloud.paas.yorc;

import alien4cloud.plugin.archives.AbstractPluginArchiveService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class YorcArchiveService extends AbstractPluginArchiveService {

    @Override
    protected Logger getLogger() {
        return log;
    }
}
