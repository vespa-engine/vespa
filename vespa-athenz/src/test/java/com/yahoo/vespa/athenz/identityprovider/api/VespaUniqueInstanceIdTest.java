package com.yahoo.vespa.athenz.identityprovider.api;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author bjorncs
 */
public class VespaUniqueInstanceIdTest {

    @Test
    public void can_serialize_to_and_deserialize_from_string() {
        VespaUniqueInstanceId id =
                new VespaUniqueInstanceId(1, "cluster-id", "instance", "application", "tenant", "region", "environment");
        String stringRepresentation = id.asDottedString();
        String expectedStringRepresentation = "1.cluster-id.instance.application.tenant.region.environment";
        assertEquals(expectedStringRepresentation, stringRepresentation);
        VespaUniqueInstanceId deserializedId = VespaUniqueInstanceId.fromDottedString(stringRepresentation);
        assertEquals(id, deserializedId);
    }

}