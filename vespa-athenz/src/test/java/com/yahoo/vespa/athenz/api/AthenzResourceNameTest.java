package com.yahoo.vespa.athenz.api;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author bjorncs
 */
public class AthenzResourceNameTest {

    @Test
    public void can_serialize_and_deserialize_to_string() {
        AthenzResourceName resourceName = new AthenzResourceName(new AthenzDomain("domain"), "entity");
        String resourceNameString = resourceName.toResourceNameString();
        assertEquals("domain:entity", resourceNameString);
        AthenzResourceName deserializedResourceName = AthenzResourceName.fromString(resourceNameString);
        assertEquals(deserializedResourceName, resourceName);
    }

}