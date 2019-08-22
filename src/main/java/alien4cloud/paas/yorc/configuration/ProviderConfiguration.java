package alien4cloud.paas.yorc.configuration;

import alien4cloud.paas.IPaaSProviderConfiguration;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormLabel;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
@NoArgsConstructor
@FormProperties({"urlYorc", "insecureTLS", "caCertificate", "clientKey", "clientCertificate", "undeployStopOnError", "connectionTimeout", "socketTimeout", "executorThreadPoolSize", "IOThreadCount", "pollingRetryDelay", "connectionMaxPoolSize", "connectionEvictionPeriod", "connectionTtl", "connectionMaxIdleTime", "registryEvictionPerdiod", "registryEntryTtl", "cleanupDeploymentsPeriod" })
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderConfiguration implements IPaaSProviderConfiguration {

    /**
     * Sequence Number
     */
    private static final AtomicInteger ID = new AtomicInteger(0);

    @FormPropertyDefinition(
            type = "string",
            defaultValue= "http://127.0.0.1:8800",
            description = "URL of a Yorc REST API instance.",
            constraints = @FormPropertyConstraint(pattern = "https?://.+")
    )
    @FormLabel("Yorc URL")
    private String urlYorc = "http://127.0.0.1:8800";

    @FormPropertyDefinition(
            type = "boolean",
            description = "Do not check host certificate. " +
                "This is not recommended for production use " +
                "and may expose to man in the middle attacks."
    )
    @FormLabel("Insecure TLS")
    private Boolean insecureTLS;

    @FormPropertyDefinition(
            type = "string",
            description = "Trusted Certificate Authority content"
    )
    @FormLabel("CA certificate")
    private String caCertificate;

    @FormPropertyDefinition(
            type = "string",
            description = "PKCS #8 encoded private key  content")
    @FormLabel("Client key")
    private String clientKey;

    @FormPropertyDefinition(
            type = "string",
            description = "Client certificate content")
    @FormLabel("Client certificate")
    private String clientCertificate;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "10",
            description = "Connection timeout in seconds."
    )
    private Integer connectionTimeout = 10;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "900",
            description = "Socket timeout in seconds. Because communication with Yorc use long polling requests, should not be a too small value."
    )
    private Integer socketTimeout = 900;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "4",
            description = "executorThreadPoolSize: number of threads used to execute actions that are not handled by IO threads (zip, scheduled tasks ...)."
    )
    private Integer executorThreadPoolSize = 4;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "4",
            description = "IOThreadCount: number of threads used to handle non blocking HTTP operations. This number should not be greater than the number of available processors on the host."
    )
    private Integer IOThreadCount = 4;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "2",
            description = "pollingRetryDelay: in seconds, the delay before reconnecting to poll events / log when long polling connection is in error."
    )
    private Integer pollingRetryDelay = 2;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "20",
            description = "connectionMaxPoolSize: maximum number of connections in the pool."
    )
    private Integer connectionMaxPoolSize = 20;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "60",
            description = "connectionEvictionPeriod: in seconds, the period used to run connection eviction."
    )
    private Integer connectionEvictionPeriod = 60;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "300",
            description = "connectionTtl: in seconds, the max time to live for a connection."
    )
    private Integer connectionTtl = 300;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "120",
            description = "connectionMaxIdleTime: in seconds, the max time an IDLE connection is kept in the pool."
    )
    private Integer connectionMaxIdleTime = 120;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "120",
            description = "registryEvictionPeriod: in seconds, the period used to run registry eviction."
    )
    private Integer registryEvictionPerdiod = 120;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "600",
            description = "registryEntryTtl: in seconds, the max time to live for a registry entry."
    )
    private Integer registryEntryTtl = 600;

    @FormPropertyDefinition(
            type = "integer",
            defaultValue = "300",
            description = "cleanupDeploymentsPeriod: in seconds, the period used to check deployments."
    )
    private Integer cleanupDeploymentsPeriod = 300;

    @FormPropertyDefinition(
            type = "boolean",
            description = "Undeploy should stop when an error occurs."
    )
    private Boolean undeployStopOnError = Boolean.FALSE;

    private String orchestratorName;
    private String orchestratorId;

    public synchronized  String getOrchestratorIdentifier() {
        if (orchestratorName == null) {
            orchestratorName = Integer.toString(ID.getAndIncrement());
        }
        return orchestratorName.replaceAll("\\W", "");
    }

}
