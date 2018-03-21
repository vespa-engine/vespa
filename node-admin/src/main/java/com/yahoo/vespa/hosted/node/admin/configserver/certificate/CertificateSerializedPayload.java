// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * Contains PEM formatted signed certificate
 * TODO: Combine with its counterpart in athenz-identity-provider-service?
 *
 * @author freva
 */
public class CertificateSerializedPayload {

    @JsonProperty("certificate") public final X509Certificate certificate;

    @JsonCreator
    public CertificateSerializedPayload(@JsonProperty("certificate") @JsonDeserialize(using = CertificateDeserializer.class)
                                        X509Certificate certificate) {
        this.certificate = certificate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CertificateSerializedPayload that = (CertificateSerializedPayload) o;

        return certificate.equals(that.certificate);
    }

    @Override
    public int hashCode() {
        return certificate.hashCode();
    }

    @Override
    public String toString() {
        return "CertificateSerializedPayload{" +
                "certificate='" + certificate + '\'' +
                '}';
    }

    public static class CertificateDeserializer extends JsonDeserializer<X509Certificate> {
        @Override
        public X509Certificate deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            return X509CertificateUtils.fromPem(jsonParser.getValueAsString());
        }
    }
}
