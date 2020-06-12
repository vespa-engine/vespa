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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class LogReaderTest {

    private final FileSystem fileSystem = TestFileSystem.create();
    private final Path logDirectory = fileSystem.getPath("/opt/vespa/logs");

    private static final String log1 = "0.1\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n";
    private static final String log2 = "0.15\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstderr\twarning\twarning\n";
    private static final String log3 = "0.2\t17491290-v6-1.ostk.bm2.prod.ne1.yahoo.com\t5480\tcontainer\tstderr\twarning\tjava.lang.NullPointerException\\n\\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\\n\\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)\n";

    @Before
    public void setup() throws IOException {
        Files.createDirectories(logDirectory.resolve("subfolder"));

        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("log1.log.gz"), compress(log1)),
                FileTime.from(Instant.ofEpochMilli(123)));
        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("subfolder/log2.log.gz"), compress(log2)),
                FileTime.from(Instant.ofEpochMilli(180)));
        Files.setLastModifiedTime(
                Files.write(logDirectory.resolve("subfolder/log3.log"), log3.getBytes(UTF_8)),
                FileTime.from(Instant.ofEpochMilli(234)));

    }

    @Test
    public void testThatLogsOutsideRangeAreExcluded() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.ofEpochMilli(150), Instant.ofEpochMilli(160));

        assertEquals("", decompress(baos.toByteArray()));
    }

    @Test
    public void testThatLogsNotMatchingRegexAreExcluded() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*3\\.log"));
        logReader.writeLogs(baos, Instant.ofEpochMilli(0), Instant.ofEpochMilli(300));

        assertEquals(log3, decompress(baos.toByteArray()));
    }

    @Test
    public void testZippedStreaming() throws IOException {
        ByteArrayOutputStream zippedBaos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(zippedBaos, Instant.ofEpochMilli(0), Instant.ofEpochMilli(300));

        assertEquals(log1 + log2 + log3, decompress(zippedBaos.toByteArray()));
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
