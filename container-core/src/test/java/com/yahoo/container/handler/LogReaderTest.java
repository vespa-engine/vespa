// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.junit.Assert.*;

public class LogReaderTest {

    ByteArrayOutputStream outputStream;

    @Before
    public void setup() {
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void testThatFilesAreWrittenCorrectlyToOutputStream() throws Exception{
        String logDirectory = "src/test/resources/logfolder/";
        LogReader logReader = new LogReader();
        JSONObject json = logReader.readLogs(logDirectory, 21, Instant.now().toEpochMilli());
        String expected = "{\"subfolder-log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxl\",\"log1.log\":\"VGhpcyBpcyBvbmUgbG9nIGZpbGU=\"}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testThatLogsOutsideRangeAreExcluded() throws Exception {
        String logDirectory = "src/test/resources/logfolder/";
        LogReader logReader = new LogReader();
        JSONObject json = logReader.readLogs(logDirectory, Long.MAX_VALUE, Long.MIN_VALUE);
        String expected = "{}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }
}
