// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

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
public class InstanceRegisterInformation {
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
    private final boolean token = true;

    public InstanceRegisterInformation(String provider, String domain, String service, String attestationData, String csr) {
        this.provider = provider;
        this.domain = domain;
        this.service = service;
        this.attestationData = attestationData;
        this.csr = csr;
    }
}
