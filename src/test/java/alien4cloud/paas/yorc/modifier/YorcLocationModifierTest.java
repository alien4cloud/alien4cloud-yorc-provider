package alien4cloud.paas.yorc.modifier;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionLog;
import org.alien4cloud.alm.deployment.configuration.flow.ITopologyModifier;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.normative.constants.NormativeComputeConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.common.MetaPropertiesService;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.common.MetaPropertyTarget;
import alien4cloud.model.common.Tag;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import alien4cloud.tosca.context.ToscaContext;

/**
 * YorcLocationModifierTest
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:alien4cloud/paas/yorc/modifier/application-context-test.xml")
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
public class YorcLocationModifierTest {

    @Autowired
    private YorcLocationModifier modifier;

    @Autowired
    private MetaPropertiesService metaPropServiceMock;

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class ContextConfiguration {
        @Bean
        public ProviderConfiguration getPC() {
            return new ProviderConfiguration();
        }

        @Bean(value = "kubernetes-final-modifier")
        public ITopologyModifier getKubernetesModifier() {
            return mock(ITopologyModifier.class);
        }

        @Bean
        public MetaPropertiesService getMetaPropSrv() {
            return mock(MetaPropertiesService.class);
        }

        @Bean("alien-es-dao")
        public IGenericSearchDAO getalienDAO() {
            return mock(IGenericSearchDAO.class);
        }

    }

    @Before
    public void initialize() {
        reset(metaPropServiceMock);
    }

    @After
    public void cleanup() {
        ToscaContext.destroy();
    }

    @Test
    public void testWithNoMetaProp() {

        Topology topology = Mockito.mock(Topology.class);
        Mockito.when(topology.getArchiveName()).thenReturn("my-topo");
        Mockito.when(topology.getArchiveVersion()).thenReturn("1.0.0-SNAPSHOT");

        NodeTemplate computeTemplate = Mockito.mock(NodeTemplate.class);
        Mockito.when(topology.getNodeTemplates()).thenReturn(Collections.singletonMap("Compute", computeTemplate));
        Mockito.when(computeTemplate.getType()).thenReturn(NormativeComputeConstants.COMPUTE_TYPE);
        Mockito.when(computeTemplate.getName()).thenReturn("Compute");
        List<Tag> tags = mock(List.class);
        Mockito.when(computeTemplate.getTags()).thenReturn(tags);

        FlowExecutionLog log = Mockito.mock(FlowExecutionLog.class);
        FlowExecutionContext context = Mockito.mock(FlowExecutionContext.class);
        Mockito.when(context.log()).thenReturn(log);
        Mockito.when(context.getLog()).thenReturn(log);

        // Locations map
        Map<String, Object> execCache = mock(Map.class);
        Map<String, Location> locMap = Maps.newHashMap();
        Mockito.when(context.getExecutionCache()).thenReturn(execCache);
        when(execCache.get(FlowExecutionContext.DEPLOYMENT_LOCATIONS_MAP_CACHE_KEY)).thenReturn(locMap);
        Location loc = mock(Location.class);
        locMap.put("someLoc", loc);
        when(loc.getName()).thenReturn("someLocation");

        modifier.process(topology, context);

        Mockito.verify(log, Mockito.never()).error(Mockito.anyString());
        Mockito.verify(log, Mockito.never()).error(Mockito.anyString(), Mockito.anyVararg());
        Mockito.verify(log, Mockito.never()).warn(Mockito.anyString());
        Mockito.verify(log, Mockito.never()).warn(Mockito.anyString(), Mockito.anyVararg());

        Mockito.verify(computeTemplate, Mockito.times(1)).getTags();

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        Mockito.verify(tags).add(captor.capture());
        assertEquals("location", captor.getValue().getName());
        assertEquals("someLocation", captor.getValue().getValue());

    }

    @Test
    public void testWithMetaProp() {

        Topology topology = Mockito.mock(Topology.class);
        Mockito.when(topology.getArchiveName()).thenReturn("my-topo");
        Mockito.when(topology.getArchiveVersion()).thenReturn("1.0.0-SNAPSHOT");

        NodeTemplate computeTemplate = Mockito.mock(NodeTemplate.class);
        Mockito.when(topology.getNodeTemplates()).thenReturn(Collections.singletonMap("Compute", computeTemplate));
        Mockito.when(computeTemplate.getType()).thenReturn(NormativeComputeConstants.COMPUTE_TYPE);
        Mockito.when(computeTemplate.getName()).thenReturn("Compute");
        List<Tag> tags = mock(List.class);
        Mockito.when(computeTemplate.getTags()).thenReturn(tags);

        FlowExecutionLog log = Mockito.mock(FlowExecutionLog.class);
        FlowExecutionContext context = Mockito.mock(FlowExecutionContext.class);
        Mockito.when(context.log()).thenReturn(log);
        Mockito.when(context.getLog()).thenReturn(log);

        // Locations map
        Map<String, Object> execCache = mock(Map.class);
        Map<String, Location> locMap = Maps.newHashMap();
        Mockito.when(context.getExecutionCache()).thenReturn(execCache);
        when(execCache.get(FlowExecutionContext.DEPLOYMENT_LOCATIONS_MAP_CACHE_KEY)).thenReturn(locMap);
        Location loc = mock(Location.class);
        locMap.put("someLoc", loc);
        when(loc.getName()).thenReturn("someLocation");


        // MetaProp
        when(metaPropServiceMock.getMetapropertykeyByName(YorcLocationModifier.YORC_LOCATION_METAPROP_NAME, MetaPropertyTarget.LOCATION)).thenReturn("somekey");
        Map<String, String> locMetaProps = Maps.newHashMap();
        locMetaProps.put("somekey", "metaPropLocation");
        when(loc.getMetaProperties()).thenReturn(locMetaProps);

        modifier.process(topology, context);

        Mockito.verify(log, Mockito.never()).error(Mockito.anyString());
        Mockito.verify(log, Mockito.never()).error(Mockito.anyString(), Mockito.anyVararg());
        Mockito.verify(log, Mockito.never()).warn(Mockito.anyString());
        Mockito.verify(log, Mockito.never()).warn(Mockito.anyString(), Mockito.anyVararg());

        Mockito.verify(computeTemplate, Mockito.times(1)).getTags();

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        Mockito.verify(tags).add(captor.capture());
        assertEquals("location", captor.getValue().getName());
        assertEquals("metaPropLocation", captor.getValue().getValue());

    }

}