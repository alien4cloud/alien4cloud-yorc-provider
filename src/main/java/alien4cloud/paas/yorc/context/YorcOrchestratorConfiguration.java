package alien4cloud.paas.yorc.context;

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

import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@ComponentScan(basePackages = {
        "alien4cloud.paas.yorc.context",
        "alien4cloud.paas.yorc.tasks"
})
public class YorcOrchestratorConfiguration {

    /**
     * Connection time out
     */
    private static final int CONNECTION_TIMEOUT = 10000;

    /**
     * Socket timeout (15 min because of long polling)
     */
    private static final int SOCKET_TIMEOUT = 900000;

    /**
     * Sequence Number
     */
    private static final AtomicInteger ID = new AtomicInteger(0);

    /**
     * @return an identifier bound to the context that will be used for naming pools
     */
    @Bean
    String contextName() {
        return "yorc-"+ ID.incrementAndGet();
    }

    /**
     * Thread factory for task threads
     * @return
     */
    @Bean
    ThreadFactory taskThreadFactory() {
        BasicThreadFactory.Builder builder = new BasicThreadFactory.Builder();
        return builder.namingPattern(contextName() + "-task-%d").build();
    }

    /**
     * Thread factory for io threads
     * @return
     */
    @Bean
    ThreadFactory httpThreadFactory() {
        BasicThreadFactory.Builder builder = new BasicThreadFactory.Builder();
        return builder.namingPattern(contextName() + "-http-%d").build();
    }

    @Bean
    @SneakyThrows(IOReactorException.class)
    ConnectingIOReactor ioReactor() {
        IOReactorConfig config = IOReactorConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSoTimeout(SOCKET_TIMEOUT)
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .build();

        return new DefaultConnectingIOReactor(config,httpThreadFactory());
    }

    /**
     * @return the executor service for our Runnable
     */
    @Bean
    ScheduledExecutorService executorService() {
        ScheduledExecutorService svc = Executors.newScheduledThreadPool(4 , taskThreadFactory() );
        // TODO: Use SysProp for pool size

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
