// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.identityprovider.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class EntityBindingsMapperTest {

    @Test
    public void reads_unknown_json_members() throws IOException {
        var iddoc = """
                {
                  "provider-unique-id": "0.cluster.instance.app.tenant.us-west-1.test.node",
                  "provider-service": "domain.service",
                  "configserver-hostname": "cfg",
                  "instance-hostname": "host",
                  "created-at": 12345.0,
                  "ip-addresses": [],
                  "identity-type": "node",
                  "cluster-type": "admin",
                  "zts-url": "https://zts.url/",
                  "unknown-string": "string-value",
                  "unknown-object": { "member-in-unknown-object": 123 }
                }
                """;
        var originalJson =
                """
                {
                  "signature": "sig",
                  "signing-key-version": 0,
                  "document-version": 4,
                  "data": "%s"
                }
                """.formatted(Base64.getEncoder().encodeToString(iddoc.getBytes(StandardCharsets.UTF_8)));
        var entity = EntityBindingsMapper.fromString(originalJson);
        assertEquals(2, entity.identityDocument().unknownAttributes().size(), entity.identityDocument().unknownAttributes().toString());
        var json = EntityBindingsMapper.toAttestationData(entity);

        // For the new iddoc format the identity document should be unchanged during serialization/deserialization,
        // i.e the signed identity document should be unchanged
        assertEquals(EntityBindingsMapper.mapper.readTree(originalJson), EntityBindingsMapper.mapper.readTree(json));
    }



}
