// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * Used for deserializing response from ZTS
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceIdentity {
    @JsonProperty("x509Certificate") private final X509Certificate x509Certificate;
    @JsonProperty("serviceToken") private final String serviceToken;

    public InstanceIdentity(@JsonProperty("x509Certificate") @JsonDeserialize(using = X509CertificateDeserializer.class)
                                    X509Certificate x509Certificate,
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

    public static class X509CertificateDeserializer extends JsonDeserializer<X509Certificate> {
        @Override
        public X509Certificate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return CryptoUtils.parseCertificate(parser.getValueAsString());
        }
    }

}
