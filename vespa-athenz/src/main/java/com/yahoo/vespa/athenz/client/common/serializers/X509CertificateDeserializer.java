// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.common.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.yahoo.security.X509CertificateUtils;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class X509CertificateDeserializer extends JsonDeserializer<X509Certificate> {
    @Override
    public X509Certificate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return X509CertificateUtils.fromPem(parser.getValueAsString());
    }
}

