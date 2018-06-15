// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yahoo.vespa.athenz.client.zts.bindings.serializers.X509CertificateDeserializer;

import java.security.cert.X509Certificate;

/**
 * Used for deserializing response from ZTS
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceIdentityCredentials {
    @JsonProperty("x509Certificate") private final X509Certificate x509Certificate;
    @JsonProperty("serviceToken") private final String serviceToken;

    public InstanceIdentityCredentials(
            @JsonProperty("x509Certificate") @JsonDeserialize(using = X509CertificateDeserializer.class) X509Certificate x509Certificate,
            @JsonProperty("serviceToken") String serviceToken) {
        this.x509Certificate = x509Certificate;
        this.serviceToken = serviceToken;
    }

    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }

    public String getServiceToken() {
        return serviceToken;
    }

}
