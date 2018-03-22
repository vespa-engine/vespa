// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrUtils;

import java.io.IOException;

/**
 * Contains PEM formatted Certificate Signing Request (CSR)
 *
 * @author freva
 */
public class CsrSerializedPayload {

    @JsonProperty("csr") public final Pkcs10Csr csr;

    @JsonCreator
    public CsrSerializedPayload(@JsonProperty("csr") @JsonDeserialize(using = CertificateRequestDeserializer.class)
                                        Pkcs10Csr csr) {
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

    public static class CertificateRequestDeserializer extends JsonDeserializer<Pkcs10Csr> {
        @Override
        public Pkcs10Csr deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            return Pkcs10CsrUtils.fromPem(jsonParser.getValueAsString());
        }
    }
}
