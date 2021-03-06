package alien4cloud.paas.yorc.model;

import lombok.*;
import org.elasticsearch.annotation.ESObject;
import org.elasticsearch.annotation.Id;
import org.elasticsearch.annotation.NumberField;
import org.elasticsearch.mapping.IndexType;

@Getter
@Setter
@ToString
@ESObject
@NoArgsConstructor
@AllArgsConstructor
public class LogEventIndex {
    @Id
    private String id;

    @NumberField(index = IndexType.not_analyzed)
    private Long index;
}
