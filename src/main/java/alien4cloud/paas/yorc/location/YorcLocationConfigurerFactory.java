package alien4cloud.paas.yorc.location;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.paas.yorc.YorcPluginFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Component that creates location configurer for Yorc.
 */
@Slf4j
@Component
public class YorcLocationConfigurerFactory extends AbstractLocationConfigurerFactory {

    @Override
    protected ILocationConfiguratorPlugin newInstanceBasedOnLocation(String locationType) {
        AbstractLocationConfigurer configurer = null;
        switch (locationType != null ? locationType : "") {
            case YorcPluginFactory.OPENSTACK:
                configurer = applicationContext.getBean(YorcOpenStackLocationConfigurer.class);
                break;
            case YorcPluginFactory.KUBERNETES:
                configurer = applicationContext.getBean(YorcKubernetesLocationConfigurer.class);
                break;
            case YorcPluginFactory.SLURM:
                configurer = applicationContext.getBean(YorcSlurmLocationConfigurer.class);
                break;
            case YorcPluginFactory.AWS:
                configurer = applicationContext.getBean(YorcAWSLocationConfigurer.class);
                break;
            case YorcPluginFactory.GOOGLE:
                configurer = applicationContext.getBean(YorcGoogleLocationConfigurer.class);
                break;
            case YorcPluginFactory.HOSTS_POOL:
                configurer = applicationContext.getBean(YorcHostsPoolLocationConfigurer.class);
                break;
            case YorcPluginFactory.MAAS:
                configurer = applicationContext.getBean(YorcMaasLocationConfigurer.class);
                break;
            default:
                log.warn("The \"%s\" location type is not handled", locationType);
        }
        return configurer;
    }
}