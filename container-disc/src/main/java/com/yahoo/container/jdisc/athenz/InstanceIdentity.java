package com.yahoo.container.jdisc.athenz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Used for deserializing response from ZTS
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class InstanceIdentity {
    @JsonProperty("attributes")
    Map<String, String> attributes;
    @JsonProperty("provider")
    private String provider;
    @JsonProperty("name")
    private String name;
    @JsonProperty("instanceId")
    private String instanceId;
    @JsonProperty("x509Certificate")
    private String x509Certificate;
    @JsonProperty("x509CertificateSigner")
    private String x509CertificateSigner;
    @JsonProperty("sshCertificate")
    private String sshCertificate;
    @JsonProperty("sshCertificateSigner")
    private String sshCertificateSigner;
    @JsonProperty("serviceToken")
    private String serviceToken;

    public String getX509Certificate() {
        return x509Certificate;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}
