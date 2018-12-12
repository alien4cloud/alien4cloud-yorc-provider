package alien4cloud.paas.yorc.model;

import lombok.*;
import org.elasticsearch.annotation.ESObject;
import org.elasticsearch.annotation.Id;
import org.elasticsearch.annotation.NumberField;
import org.elasticsearch.annotation.StringField;
import org.elasticsearch.mapping.IndexType;

@Getter
@Setter
@ToString
@ESObject
@NoArgsConstructor
@AllArgsConstructor
public class EventIndex {

    @Id
    private String id;

    @NumberField(index = IndexType.not_analyzed)
    private Integer index;
}
