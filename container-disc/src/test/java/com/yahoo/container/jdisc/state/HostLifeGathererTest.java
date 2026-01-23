// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author olaa
 */
public class HostLifeGathererTest {

    @Test
    void host_is_alive() {
        JsonNode packet = HostLifeGatherer.getHostLifePacket();
        JsonNode metrics = packet.get("metrics");
        assertEquals("host_life", packet.get("application").textValue());
        assertEquals(1, metrics.get("alive").intValue());
        assertTrue(packet.get("dimensions").hasNonNull("vespaVersion"));
    }
}
