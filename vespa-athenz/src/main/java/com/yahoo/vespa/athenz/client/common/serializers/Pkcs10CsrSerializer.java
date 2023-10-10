// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.common.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrUtils;

import java.io.IOException;

/**
 * @author bjorncs
 */
public class Pkcs10CsrSerializer extends JsonSerializer<Pkcs10Csr> {
    @Override
    public void serialize(Pkcs10Csr csr, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(Pkcs10CsrUtils.toPem(csr));
    }
}
