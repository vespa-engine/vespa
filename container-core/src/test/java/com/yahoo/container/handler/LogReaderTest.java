// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;

public class LogReaderTest {

    private final Path logDirectory = Paths.get("src/test/resources/logfolder/");

    @Test
    public void testThatFilesAreWrittenCorrectlyToOutputStream() throws Exception{
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        JSONObject json = logReader.readLogs(Instant.ofEpochMilli(21), Instant.now());
        String expected = "{\"subfolder-log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxlCg==\",\"log1.log\":\"VGhpcyBpcyBvbmUgbG9nIGZpbGUK\"}";
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
        String expected = "{\"subfolder-log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxlCg==\"}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testZippedStreaming() throws IOException {
        ByteArrayOutputStream zippedBaos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(zippedBaos, Instant.ofEpochMilli(21), Instant.now());
        GZIPInputStream unzippedIs = new GZIPInputStream(new ByteArrayInputStream(zippedBaos.toByteArray()));

        Scanner s = new Scanner(unzippedIs).useDelimiter("\\A");
        String actual = s.hasNext() ? s.next() : "";

        String expected = "This is one log file\nThis is another log file\n";
        assertEquals(expected, actual);
    }
}
