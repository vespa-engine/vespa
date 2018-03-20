// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrUtils;

import java.io.IOException;

/**
 * Contains PEM formatted Certificate Signing Request (CSR)
 * TODO: Combine with its counterpart in athenz-identity-provider-service?
 *
 * @author freva
 */
public class CsrSerializedPayload {

    @JsonProperty("csr") @JsonSerialize(using = CertificateRequestSerializer.class)
    public final Pkcs10Csr csr;

    @JsonCreator
    public CsrSerializedPayload(@JsonProperty("csr") Pkcs10Csr csr) {
        this.csr = csr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CsrSerializedPayload that = (CsrSerializedPayload) o;

        return csr.equals(that.csr);
    }

    @Override
    public int hashCode() {
        return csr.hashCode();
    }

    @Override
    public String toString() {
        return "CsrSerializedPayload{" +
                "csr='" + csr + '\'' +
                '}';
    }

    public static class CertificateRequestSerializer extends JsonSerializer<Pkcs10Csr> {
        @Override
        public void serialize(Pkcs10Csr csr, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(Pkcs10CsrUtils.toPem(csr));
        }
    }
}
