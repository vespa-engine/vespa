// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;


/**
 * @author olaa
 */
public class HostLifeGathererTest {

    @Test
    public void host_is_alive() throws JSONException {
        JSONObject packet = HostLifeGatherer.getHostLifePacket(new MockFileWrapper());
        JSONObject metrics = packet.getJSONObject("metrics");
        assertEquals("host_life", packet.getString("application"));
        assertEquals(0, packet.getInt("status_code"));
        assertEquals("OK", packet.getString("status_msg"));

        assertEquals(123l, metrics.getLong("uptime"));
        assertEquals(1, metrics.getInt("alive"));

    }

    static class MockFileWrapper extends FileWrapper {

        @Override
        long getFileAgeInSeconds(Path path) {
            return 123;
        }
    }
}
