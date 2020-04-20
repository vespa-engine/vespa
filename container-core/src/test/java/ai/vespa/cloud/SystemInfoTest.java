// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class SystemInfoTest {

    @Test
    public void testSystemInfo() {
        Zone zone = new Zone(Environment.dev, "us-west-1");
        SystemInfo info = new SystemInfo(zone);
        assertEquals(zone, info.zone());
    }

    @Test
    public void testZone() {
        Zone zone = Zone.from("dev.us-west-1");
        zone = Zone.from(zone.toString());
        assertEquals(Environment.dev, zone.environment());
        assertEquals("us-west-1", zone.region());
        Zone sameZone = Zone.from("dev.us-west-1");
        assertEquals(sameZone.hashCode(), zone.hashCode());
        assertEquals(sameZone, zone);

        try {
            Zone.from("invalid");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("A zone string must be on the form [environment].[region], but was 'invalid'",
                         e.getMessage());
        }

        try {
            Zone.from("invalid.us-west-1");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Invalid zone 'invalid.us-west-1': No environment named 'invalid'", e.getMessage());
        }
    }

}
