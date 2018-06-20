// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author bjorncs
 */
public class X509CertificateListDeserializer extends JsonDeserializer<List<X509Certificate>> {

    @Override
    public List<X509Certificate> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        return X509CertificateUtils.certificateListFromPem(parser.getValueAsString());
    }
}
