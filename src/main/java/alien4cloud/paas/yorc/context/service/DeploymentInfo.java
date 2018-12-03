package alien4cloud.paas.yorc.context.service;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.yorc.context.rest.response.Event;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class DeploymentInfo {

    DeploymentInfo() {
    }

    @Getter
    @Setter
    private DeploymentStatus status;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private PaaSTopologyDeploymentContext context;

    private PublishSubject<Event> events = PublishSubject.create();


    PublishSubject<Event> getEventsAsSubject() {
        return events;
    }

    public Observable<Event> getEvents() {
        return events;
    }

}
