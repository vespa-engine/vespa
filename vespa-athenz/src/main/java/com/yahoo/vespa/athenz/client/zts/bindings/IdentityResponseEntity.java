// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yahoo.vespa.athenz.client.zts.bindings.serializers.X509CertificateDeserializer;
import com.yahoo.vespa.athenz.client.zts.bindings.serializers.X509CertificateListDeserializer;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Identity response entity
 *
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityResponseEntity {

    private final X509Certificate certificate;
    private final List<X509Certificate> caCertificateBundle;

    @JsonCreator
    public IdentityResponseEntity(
            @JsonProperty("certificate") @JsonDeserialize(using = X509CertificateDeserializer.class) X509Certificate certificate,
            @JsonProperty("caCertBundle") @JsonDeserialize(using = X509CertificateListDeserializer.class) List<X509Certificate> caCertificateBundle) {
        this.certificate = certificate;
        this.caCertificateBundle = caCertificateBundle;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public List<X509Certificate> caCertificateBundle() {
        return caCertificateBundle;
    }
}
