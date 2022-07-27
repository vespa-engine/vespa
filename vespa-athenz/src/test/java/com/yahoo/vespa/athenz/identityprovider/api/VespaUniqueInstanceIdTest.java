// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.athenz.identityprovider.api.IdentityType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class VespaUniqueInstanceIdTest {

    @Test
    void can_serialize_to_and_deserialize_from_string() {
        VespaUniqueInstanceId id =
                new VespaUniqueInstanceId(1, "cluster-id", "instance", "application", "tenant", "region", "environment", TENANT);
        String stringRepresentation = id.asDottedString();
        String expectedStringRepresentation = "1.cluster-id.instance.application.tenant.region.environment.tenant";
        assertEquals(expectedStringRepresentation, stringRepresentation);
        VespaUniqueInstanceId deserializedId = VespaUniqueInstanceId.fromDottedString(stringRepresentation);
        assertEquals(id, deserializedId);
    }

}
