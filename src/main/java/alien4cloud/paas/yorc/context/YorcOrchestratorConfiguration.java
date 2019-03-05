package alien4cloud.paas.yorc.context;

import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@ComponentScan(basePackages = {
        "alien4cloud.paas.yorc.context"
})
@EnableMBeanExport

public class YorcOrchestratorConfiguration {

    /**
     * @return an identifier bound to the context that will be used for naming pools
     */
    @Bean
    String contextName() {
        return "yorc-"+ configuration.getOrchestratorIdentifier();
    }

    @Resource
    private ProviderConfiguration configuration;

    /**
     * Thread factory for task threads
     * @return
     */
    @Bean("task-thread-factory")
    ThreadFactory taskThreadFactory() {
        BasicThreadFactory.Builder builder = new BasicThreadFactory.Builder();
        return builder.namingPattern(contextName() + "-task-%d").build();
    }

    /**
     * Thread factory for io threads
     * @return
     */
    @Bean("http-thread-factory")
    ThreadFactory httpThreadFactory() {
        BasicThreadFactory.Builder builder = new BasicThreadFactory.Builder();
        return builder.namingPattern(contextName() + "-http-%d").build();
    }

    @Bean
    @SneakyThrows(IOReactorException.class)
    ConnectingIOReactor ioReactor() {

        configuration.getUrlYorc();

        IOReactorConfig config = IOReactorConfig.custom()
                .setConnectTimeout(configuration.getConnectionTimeout() * 1000)
                .setSoTimeout(configuration.getSocketTimeout() * 1000)
                .setIoThreadCount(configuration.getIOThreadCount())
                .build();

        log.info("IOReactor will be configured using : " + config);

        return new DefaultConnectingIOReactor(config,httpThreadFactory());
    }

    /**
     * @return the executor service for our Runnable
     */
    @Bean
    ExecutorService executorService() {
        log.info("Executor will use {} threads.", configuration.getExecutorThreadPoolSize());
        ExecutorService svc = Executors.newFixedThreadPool(configuration.getExecutorThreadPoolSize() , taskThreadFactory() );
        return svc;
    }

    @Bean
    Scheduler scheduler() {
        return Schedulers.from(executorService());
    }

    @PreDestroy
    private void term() {
        executorService().shutdown();
    }
}
