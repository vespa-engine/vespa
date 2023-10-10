// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yahoo.vespa.athenz.client.common.serializers.X509CertificateDeserializer;

import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleCertificateResponseEntity {
    public final X509Certificate certificate;

    @JsonCreator
    public RoleCertificateResponseEntity(@JsonProperty("x509Certificate") @JsonDeserialize(using = X509CertificateDeserializer.class) X509Certificate certificate) {
        this.certificate = certificate;
    }
}
