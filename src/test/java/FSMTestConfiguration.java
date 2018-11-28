import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan(basePackages = {
        "alien4cloud.paas.yorc.context.service.fsm"
})
public class FSMTestConfiguration {
}
