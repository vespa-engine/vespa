package com.yahoo.container.logging;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.test.json.JsonTestHelper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * @author bjorncs
 */
class JsonConnectionLogWriterTest {

    @Test
     void test_serialization() throws IOException {
        var id = UUID.randomUUID();
        var instant = Instant.parse("2021-01-13T12:12:12Z");
        ConnectionLogEntry entry = ConnectionLogEntry.builder(id, instant)
                .withPeerPort(1234)
                .build();
        String expectedJson = "{" +
                "\"id\":\""+id.toString()+"\"," +
                "\"timestamp\":\"2021-01-13T12:12:12Z\"," +
                "\"peerPort\":1234" +
                "}";

        JsonConnectionLogWriter writer = new JsonConnectionLogWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(entry, out);
        String actualJson = out.toString(StandardCharsets.UTF_8);
        JsonTestHelper.assertJsonEquals(actualJson, expectedJson);
    }
}