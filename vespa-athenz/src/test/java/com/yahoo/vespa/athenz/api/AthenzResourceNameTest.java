// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class AthenzResourceNameTest {

    @Test
    void can_serialize_and_deserialize_to_string() {
        AthenzResourceName resourceName = new AthenzResourceName(new AthenzDomain("domain"), "entity");
        String resourceNameString = resourceName.toResourceNameString();
        assertEquals("domain:entity", resourceNameString);
        AthenzResourceName deserializedResourceName = AthenzResourceName.fromString(resourceNameString);
        assertEquals(deserializedResourceName, resourceName);
    }

}
