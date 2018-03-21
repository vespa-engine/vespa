// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Contains PEM formatted Certificate Signing Request (CSR)
 * TODO: Combine with its counterpart in athenz-identity-provider-service?
 *
 * @author freva
 */
public class CsrSerializedPayload {

    @JsonProperty("csr") @JsonSerialize(using = CertificateRequestSerializer.class)
    public final PKCS10CertificationRequest csr;

    @JsonCreator
    public CsrSerializedPayload(@JsonProperty("csr") PKCS10CertificationRequest csr) {
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

    public static class CertificateRequestSerializer extends JsonSerializer<PKCS10CertificationRequest> {
        @Override
        public void serialize(
                PKCS10CertificationRequest csr, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
                pemWriter.writeObject(new PemObject("CERTIFICATE REQUEST", csr.getEncoded()));
                pemWriter.flush();
                gen.writeString(stringWriter.toString());
            }
        }
    }
}
