package alien4cloud.paas.yorc.context;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
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

    @Bean
    ThreadFactory threadFactory() {
        BasicThreadFactory.Builder builder = new BasicThreadFactory.Builder();
        return builder.namingPattern(contextName() + "-task-%d").build();
    }

    /**
     * @return the executor service for our Runnable
     */
    @Bean
    ScheduledExecutorService executorService() {
        ScheduledExecutorService svc = Executors.newScheduledThreadPool(4 , threadFactory() );

        svc.execute(new Runnable() {
            @Override
            public void run() {
                log.info("RUNNING!");
            }
        });
        return svc;
    }

    @PreDestroy
    private void term() {
        executorService().shutdown();
    }
}
