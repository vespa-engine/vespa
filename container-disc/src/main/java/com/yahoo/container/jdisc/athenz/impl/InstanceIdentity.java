// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Used for deserializing response from ZTS
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceIdentity {
    @JsonProperty("x509Certificate") private final String x509Certificate;
    @JsonProperty("serviceToken") private final String serviceToken;

    public InstanceIdentity(@JsonProperty("x509Certificate") String x509Certificate,
                            @JsonProperty("serviceToken") String serviceToken) {
        this.x509Certificate = x509Certificate;
        this.serviceToken = serviceToken;
    }

    public String getX509Certificate() {
        return x509Certificate;
    }

    public String getServiceToken() {
        return serviceToken;
    }
}
