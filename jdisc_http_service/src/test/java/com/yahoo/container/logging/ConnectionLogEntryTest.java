// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * @author mortent
 */
public class ConnectionLogEntryTest {

    @Test
    public void test_serialization () throws IOException {
        var id = UUID.randomUUID();
        var instant = Instant.parse("2021-01-13T12:12:12Z");
        ConnectionLogEntry entry = ConnectionLogEntry.builder(id, instant)
                .withPeerPort(1234)
                .build();

        String expected = "{" +
                          "\"id\":\""+id.toString()+"\"," +
                          "\"timestamp\":\"2021-01-13T12:12:12Z\"," +
                          "\"peerPort\":1234" +
                          "}\n";
        Assert.assertEquals(expected, entry.toJson());
    }
}
