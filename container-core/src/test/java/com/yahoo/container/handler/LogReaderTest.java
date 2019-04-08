// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.vespa.test.file.TestFileSystem;
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

        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("log1.log.gz"), compress("This is one log file\n")),
                FileTime.from(Instant.ofEpochMilli(123)));
        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("subfolder/log2.log"), "This is another log file\n".getBytes()),
                FileTime.from(Instant.ofEpochMilli(234)));

    }

    @Test
    public void testThatLogsOutsideRangeAreExcluded() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.ofEpochMilli(235), Instant.ofEpochMilli(300));
        String expected = "";
        String actual = decompress(baos.toByteArray());
        assertEquals(expected, actual);
    }

    @Test
    public void testThatLogsNotMatchingRegexAreExcluded() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*2\\.log"));
        logReader.writeLogs(baos, Instant.ofEpochMilli(21), Instant.now());
        String expected = "This is another log file\n";
        String actual = decompress(baos.toByteArray());
        assertEquals(expected, actual);
    }

    @Test
    public void testZippedStreaming() throws IOException {
        ByteArrayOutputStream zippedBaos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(zippedBaos, Instant.ofEpochMilli(21), Instant.now());

        String expected = "This is one log file\nThis is another log file\n";
        String actual = decompress(zippedBaos.toByteArray());
        assertEquals(expected, actual);
    }

    private byte[] compress(String input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream zip = new GZIPOutputStream(baos);
        zip.write(input.getBytes());
        zip.close();
        return baos.toByteArray();
    }

    private String decompress(byte[] input) throws IOException {
        if (input.length == 0) return "";
        byte[] decompressed = new GZIPInputStream(new ByteArrayInputStream(input)).readAllBytes();
        return new String(decompressed);
    }

}
