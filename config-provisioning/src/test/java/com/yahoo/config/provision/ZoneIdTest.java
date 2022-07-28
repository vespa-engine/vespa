// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.config.provision.zone.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author hmusum
 */
public class ZoneIdTest {

    private static final Environment environment = Environment.prod;
    private static final RegionName region = RegionName.from("moon-dark-side-1");

    @Test
    void testCreatingZoneId() {
        ZoneId zoneId = ZoneId.from(environment, region);
        assertEquals(region, zoneId.region());
        assertEquals(environment, zoneId.environment());
    }

    @Test
    void testSerializingAndDeserializing() {
        ZoneId zoneId = ZoneId.from(environment, region);
        assertEquals(environment.value() + "." + region.value(), zoneId.value());
        assertEquals(ZoneId.from(zoneId.value()), zoneId);

        String serializedZoneId = "some.illegal.value";
        try {
            ZoneId.from(serializedZoneId);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Cannot deserialize zone id '" + serializedZoneId + "'", e.getMessage());
        }
    }

}
