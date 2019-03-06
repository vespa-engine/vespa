// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class LogReaderTest {

    private final Path logDirectory = Paths.get("src/test/resources/logfolder/");

    @Test
    public void testThatFilesAreWrittenCorrectlyToOutputStream() throws Exception{
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        JSONObject json = logReader.readLogs(Instant.ofEpochMilli(21), Instant.now());
        String expected = "{\"subfolder-log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxl\",\"log1.log\":\"VGhpcyBpcyBvbmUgbG9nIGZpbGU=\"}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testThatLogsOutsideRangeAreExcluded() throws Exception {
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        JSONObject json = logReader.readLogs(Instant.MAX, Instant.MIN);
        String expected = "{}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testThatLogsNotMatchingRegexAreExcluded() throws Exception {
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*2\\.log"));
        JSONObject json = logReader.readLogs(Instant.ofEpochMilli(21), Instant.now());
        String expected = "{\"subfolder-log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxl\"}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }
}
