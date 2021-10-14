// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class LogReaderTest {

    private final FileSystem fileSystem = TestFileSystem.create();
    private final Path logDirectory = fileSystem.getPath("/opt/vespa/logs");

    private static final String logv11 = "3600.2\tnode1.com\t5480\tcontainer\tstdout\tinfo\tfourth\n";
    private static final String logv   = "90000.1\tnode1.com\t5480\tcontainer\tstdout\tinfo\tlast\n";
    private static final String log100 = "0.2\tnode2.com\t5480\tcontainer\tstdout\tinfo\tsecond\n";
    private static final String log101 = "0.1\tnode2.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n";
    private static final String log110 = "3600.1\tnode1.com\t5480\tcontainer\tstderr\twarning\tthird\n";
    private static final String log200 = "86400.1\tnode2.com\t5480\tcontainer\tstderr\twarning\tjava.lang.NullPointerException\\n\\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\\n\\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)\n";

    @Before
    public void setup() throws IOException {
        // Log archive paths and file names indicate what hour they contain logs for, with the start of that hour.
        // Multiple entries may exist for each hour.
        Files.createDirectories(logDirectory.resolve("1970/01/01"));
        Files.write(logDirectory.resolve("1970/01/01/00-0.gz"), compress(log100));
        Files.write(logDirectory.resolve("1970/01/01/00-1"), log101.getBytes(UTF_8));
        Files.write(logDirectory.resolve("1970/01/01/01-0.gz"), compress(log110));

        Files.createDirectories(logDirectory.resolve("1970/01/02"));
        Files.write(logDirectory.resolve("1970/01/02/00-0"), log200.getBytes(UTF_8));

        // Vespa log file names are the second-truncated timestamp of the last entry.
        // The current log file has no timestamp suffix.
        Files.write(logDirectory.resolve("vespa.log-1970-01-01.01-00-00"), logv11.getBytes(UTF_8));
        Files.write(logDirectory.resolve("vespa.log"), logv.getBytes(UTF_8));
    }

    @Test
    public void testThatLogsOutsideRangeAreExcluded() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.ofEpochMilli(150), Instant.ofEpochMilli(3601050), Optional.empty());

        assertEquals(log100 + logv11 + log110, baos.toString(UTF_8));
    }

    @Test
    public void testThatLogsNotMatchingRegexAreExcluded() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*-1.*"));
        logReader.writeLogs(baos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), Optional.empty());

        assertEquals(log101 + logv11, baos.toString(UTF_8));
    }

    @Test
    public void testZippedStreaming() {
        ByteArrayOutputStream zippedBaos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(zippedBaos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), Optional.empty());

        assertEquals(log101 + log100 + logv11 + log110 + log200 + logv, zippedBaos.toString(UTF_8));
    }

    @Test
    public void logsForSingeNodeIsRetrieved() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), Optional.of("node2.com"));

        assertEquals(log101 + log100 + log200, baos.toString(UTF_8));
    }

    private byte[] compress(String input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream zip = new GZIPOutputStream(baos);
        zip.write(input.getBytes());
        zip.close();
        return baos.toByteArray();
    }

}
