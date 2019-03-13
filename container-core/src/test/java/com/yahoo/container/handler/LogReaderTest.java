// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;

public class LogReaderTest {

    private final FileSystem fileSystem = TestFileSystem.create();
    private final Path logDirectory = fileSystem.getPath("/opt/vespa/logs");

    @Before
    public void setup() throws IOException {
        Files.createDirectories(logDirectory.resolve("subfolder"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream zipped = new GZIPOutputStream(baos);
        zipped.write("This is one log file\n".getBytes());
        zipped.close();

        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("log1.log.gz"), baos.toByteArray()),
                FileTime.from(Instant.ofEpochMilli(123)));
        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("subfolder/log2.log"), "This is another log file\n".getBytes()),
                FileTime.from(Instant.ofEpochMilli(234)));
    }

    @Test
    public void testThatFilesAreWrittenCorrectlyToOutputStream() throws Exception{
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        JSONObject json = logReader.readLogs(Instant.ofEpochMilli(21), Instant.now());
        String expected = "{\"subfolder-log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxlCg==\",\"log1.log.gz\":\"VGhpcyBpcyBvbmUgbG9nIGZpbGUK\"}";
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
