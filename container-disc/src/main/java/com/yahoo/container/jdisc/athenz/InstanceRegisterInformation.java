package com.yahoo.container.jdisc.athenz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Used for serializing request to ZTS
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class InstanceRegisterInformation {
    @JsonProperty("provider")
    private final String provider;
    @JsonProperty("domain")
    private final String domain;
    @JsonProperty("service")
    private final String service;
    @JsonProperty("attestationData")
    private final String attestationData;
    @JsonProperty("ssh")
    private final String ssh = null; // Not needed
    @JsonProperty("csr")
    private final String csr;
    @JsonProperty("token")
    private final boolean token;

    public InstanceRegisterInformation(String provider, String domain, String service, String attestationData, String csr, boolean token) {
        this.provider = provider;
        this.domain = domain;
        this.service = service;
        this.attestationData = attestationData;
        this.csr = csr;
        this.token = token;
    }
}
