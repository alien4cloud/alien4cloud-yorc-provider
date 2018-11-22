package alien4cloud.paas.yorc.configuration;

import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@FormProperties({"urlYorc", "insecureTLS"})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderConfiguration {

    @FormPropertyDefinition(
            type = "string",
            defaultValue= "http://127.0.0.1:8800",
            description = "URL of a Yorc REST API instance.",
            constraints = @FormPropertyConstraint(pattern = "https?://.+")
    )
    private String urlYorc = "http://127.0.0.1:8800";

    @FormPropertyDefinition(
            type = "boolean",
            description = "Do not check host certificate. " +
                "This is not recommended for production use " +
                "and may expose to man in the middle attacks."
    )
    private Boolean insecureTLS;
}
