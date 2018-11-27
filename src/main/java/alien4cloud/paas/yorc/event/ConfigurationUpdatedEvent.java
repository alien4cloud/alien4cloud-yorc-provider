package alien4cloud.paas.yorc.event;

import alien4cloud.paas.yorc.configuration.ProviderConfiguration;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class ConfigurationUpdatedEvent extends ApplicationEvent {

    @Getter
    private final ProviderConfiguration configuration;

    public ConfigurationUpdatedEvent(Object source, ProviderConfiguration configuration) {
        super(source);

        this.configuration = configuration;
    }
}
