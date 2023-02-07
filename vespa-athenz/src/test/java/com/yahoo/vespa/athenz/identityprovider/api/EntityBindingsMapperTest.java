// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.identityprovider.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class EntityBindingsMapperTest {

    @Test
    public void persists_unknown_json_members() throws IOException {
        var originalJson =
                """
                {
                  "signature": "sig",
                  "signing-key-version": 0,
                  "provider-unique-id": "0.cluster.instance.app.tenant.us-west-1.test.node",
                  "provider-service": "domain.service",
                  "document-version": 2,
                  "configserver-hostname": "cfg",
                  "instance-hostname": "host",
                  "created-at": 12345.0,
                  "ip-addresses": [],
                  "identity-type": "node",
                  "cluster-type": "admin",
                  "unknown-string": "string-value",
                  "unknown-object": { "member-in-unknown-object": 123 }
                }
                """;
        var entity = EntityBindingsMapper.fromString(originalJson);
        assertEquals(2, entity.unknownAttributes().size(), entity.unknownAttributes().toString());
        var json = EntityBindingsMapper.toAttestationData(entity);

        var expectedMemberInJson = "member-in-unknown-object";
        assertTrue(json.contains(expectedMemberInJson),
                   () -> "Expected JSON to contain '%s', but got \n'%s'".formatted(expectedMemberInJson, json));
        assertEquals(EntityBindingsMapper.mapper.readTree(originalJson), EntityBindingsMapper.mapper.readTree(json));
    }

}