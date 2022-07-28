// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author olaa
 */
public class HostLifeGathererTest {

    @Test
    void host_is_alive() {
        JsonNode packet = HostLifeGatherer.getHostLifePacket(new MockFileWrapper());
        JsonNode metrics = packet.get("metrics");
        assertEquals("host_life", packet.get("application").textValue());
        assertEquals(0, packet.get("status_code").intValue());
        assertEquals("OK", packet.get("status_msg").textValue());

        assertEquals(123L, metrics.get("uptime").longValue());
        assertEquals(1, metrics.get("alive").intValue());

    }

    static class MockFileWrapper extends FileWrapper {

        @Override
        long getFileAgeInSeconds(Path path) {
            return 123;
        }
    }
}
