// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.io.StringReader;

/**
 * Contains PEM formatted Certificate Signing Request (CSR)
 *
 * @author freva
 */
public class CsrSerializedPayload {

    @JsonProperty("csr") public final PKCS10CertificationRequest csr;

    @JsonCreator
    public CsrSerializedPayload(@JsonProperty("csr") @JsonDeserialize(using = CertificateRequestDeserializer.class)
                                            PKCS10CertificationRequest csr) {
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

    public static class CertificateRequestDeserializer extends JsonDeserializer<PKCS10CertificationRequest> {
        @Override
        public PKCS10CertificationRequest deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            try (PEMParser pemParser = new PEMParser(new StringReader(jsonParser.getValueAsString()))) {
                return (PKCS10CertificationRequest) pemParser.readObject();
            }
        }
    }
}
