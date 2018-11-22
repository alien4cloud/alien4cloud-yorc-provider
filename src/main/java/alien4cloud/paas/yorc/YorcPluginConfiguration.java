package alien4cloud.paas.yorc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(basePackages = { "alien4cloud.paas.yorc" }, excludeFilters = {
        @Filter(type = FilterType.REGEX, pattern = "alien4cloud\\.paas\\.yorc\\.context\\..*"),
        @Filter(type = FilterType.REGEX, pattern = "alien4cloud\\.paas\\.yorc\\.tasks\\..*")
})
public class YorcPluginConfiguration {

    @Bean("yorc-orchestrator-factory")
    public YorcPluginFactory yorcOrchestratorFactory() {
        return new YorcPluginFactory();
    }

}
