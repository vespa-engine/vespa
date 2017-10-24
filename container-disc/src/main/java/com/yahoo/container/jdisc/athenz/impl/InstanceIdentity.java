// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

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
public class InstanceIdentity {
    @JsonProperty("attributes") private final Map<String, String> attributes;
    @JsonProperty("provider") private final String provider;
    @JsonProperty("name") private final String name;
    @JsonProperty("instanceId") private final String instanceId;
    @JsonProperty("x509Certificate") private final String x509Certificate;
    @JsonProperty("x509CertificateSigner") private final String x509CertificateSigner;
    @JsonProperty("sshCertificate") private final String sshCertificate;
    @JsonProperty("sshCertificateSigner") private final String sshCertificateSigner;
    @JsonProperty("serviceToken") private final String serviceToken;

    public InstanceIdentity(
            @JsonProperty("attributes") Map<String, String> attributes,
            @JsonProperty("provider") String provider,
            @JsonProperty("name") String name,
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("x509Certificate") String x509Certificate,
            @JsonProperty("x509CertificateSigner") String x509CertificateSigner,
            @JsonProperty("sshCertificate") String sshCertificate,
            @JsonProperty("sshCertificateSigner") String sshCertificateSigner,
            @JsonProperty("serviceToken") String serviceToken) {
        this.attributes = attributes;
        this.provider = provider;
        this.name = name;
        this.instanceId = instanceId;
        this.x509Certificate = x509Certificate;
        this.x509CertificateSigner = x509CertificateSigner;
        this.sshCertificate = sshCertificate;
        this.sshCertificateSigner = sshCertificateSigner;
        this.serviceToken = serviceToken;
    }

    public String getX509Certificate() {
        return x509Certificate;
    }

    public String getServiceToken() {
        return serviceToken;
    }
}
