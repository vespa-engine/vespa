// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoggerEntryTest {

    @Test
    public void serializeDeserializationTest() throws IOException {
        LoggerEntry entry = new LoggerEntry.Builder()
                .timestamp(123456789L)
                .query(new Query("/search/?"))
                .blob("34586")
                .track("mytrack")
                .build();
        LoggerEntry entryFromJson = LoggerEntry.deserialize(entry.serialize());

        assertEquals(entry.timestamp(), entryFromJson.timestamp());
        assertEquals(entry.queryString(), entryFromJson.queryString());
        assertEquals(new String(entry.blob().array(), StandardCharsets.UTF_8),
                     new String(entryFromJson.blob().array(), StandardCharsets.UTF_8));
        assertEquals(entry.track(), entryFromJson.track());
    }
}
