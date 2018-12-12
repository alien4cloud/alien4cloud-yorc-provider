package alien4cloud.paas.yorc.dao;

import alien4cloud.audit.model.AuditConfiguration;
import alien4cloud.audit.model.AuditTrace;
import alien4cloud.dao.ESGenericSearchDAO;
import alien4cloud.exception.IndexingServiceException;
import alien4cloud.paas.yorc.model.EventIndex;
import alien4cloud.paas.yorc.model.LogEventIndex;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.beans.IntrospectionException;
import java.io.IOException;

@Component
public class YorcESDao extends ESGenericSearchDAO {

    private static final String ALIEN_YORC_INDEX = "yorc";

    @PostConstruct
    public void init() {
        try {
            getMappingBuilder().initialize("alien4cloud.paas.yorc.model");
        } catch (IntrospectionException | IOException e) {
            throw new IndexingServiceException("Could not initialize elastic search mapping builder", e);
        }
        // Audit trace index
        initIndices(ALIEN_YORC_INDEX, null, EventIndex.class, LogEventIndex.class);
        initCompleted();
    }
}
