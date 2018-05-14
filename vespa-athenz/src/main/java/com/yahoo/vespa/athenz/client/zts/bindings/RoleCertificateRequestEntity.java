// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrUtils;

import java.io.IOException;
import java.time.Duration;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleCertificateRequestEntity {
    @JsonProperty("csr")
    @JsonSerialize(using = CsrSerializer.class)
    public final Pkcs10Csr csr;

    @JsonProperty("expiryTime")
    @JsonSerialize(using = ExpirySerializer.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Duration expiryTime;

    public RoleCertificateRequestEntity(Pkcs10Csr csr, Duration expiryTime) {
        this.csr = csr;
        this.expiryTime = expiryTime;
    }

    public static class CsrSerializer extends JsonSerializer<Pkcs10Csr> {
        @Override
        public void serialize(Pkcs10Csr csr,
                              JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(Pkcs10CsrUtils.toPem(csr));
        }
    }

    public static class ExpirySerializer extends JsonSerializer<Duration> {
        @Override
        public void serialize(Duration duration,
                              JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(duration.getSeconds());
        }
    }
}
