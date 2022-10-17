// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.yahoo.compress.ZstdCompressor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogReaderTest {

    @TempDir
    public File folder;
    private Path logDirectory;

    private static final String logv11 = "3600.2\tnode1.com\t5480\tcontainer\tstdout\tinfo\tfourth\n";
    private static final String logv   = "90000.1\tnode1.com\t5480\tcontainer\tstdout\tinfo\tlast\n";
    private static final String log100a = "0.2\tnode2.com\t5480\tcontainer\tstdout\tinfo\tsecond\n";
    private static final String log100b = "0.15\tnode2.com\t5480\tcontainer\tstdout\tinfo\tfirst\n";
    private static final String log101 = "0.1\tnode2.com\t5480\tcontainer\tstdout\tinfo\tERROR: Bundle canary-application [71] Unable to get module class path. (java.lang.NullPointerException)\n";
    private static final String log110 = "3600.1\tnode1.com\t5480\tcontainer\tstderr\twarning\tthird\n";
    private static final String log200 = "86400.1\tnode2.com\t5480\tcontainer\tstderr\twarning\tjava.lang.NullPointerException\\n\\tat org.apache.felix.framework.BundleRevisionImpl.calculateContentPath(BundleRevisionImpl.java:438)\\n\\tat org.apache.felix.framework.BundleRevisionImpl.initializeContentPath(BundleRevisionImpl.java:371)\n";

    @BeforeEach
    public void setup() throws IOException {
        logDirectory = Files.createDirectories(folder.toPath().resolve("opt/vespa/logs"));
        // Log archive paths and file names indicate what hour they contain logs for, with the start of that hour.
        // Multiple entries may exist for each hour.
        Files.createDirectories(logDirectory.resolve("1970/01/01"));
        // Files may contain out-of-order entries.
        Files.write(logDirectory.resolve("1970/01/01/00-0.gz"), compress1(log100a + log100b));
        Files.writeString(logDirectory.resolve("1970/01/01/00-1"), log101);
        Files.write(logDirectory.resolve("1970/01/01/01-0.zst"), compress2(log110));

        Files.createDirectories(logDirectory.resolve("1970/01/02"));
        Files.writeString(logDirectory.resolve("1970/01/02/00-0"), log200);

        // Vespa log file names are the second-truncated timestamp of the last entry.
        // The current log file has no timestamp suffix.
        Files.writeString(logDirectory.resolve("vespa.log-1970-01-01.01-00-00"), logv11);
        Files.writeString(logDirectory.resolve("vespa.log"), logv);
    }

    private static boolean hasZstdcat() {
        try {
            return new ProcessBuilder("zstdcat", "--version").start().waitFor() == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

    @EnabledIf("hasZstdcat")
    @Test
    void testThatLogsOutsideRangeAreExcluded() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.ofEpochMilli(150), Instant.ofEpochMilli(3601050), 100, Optional.empty());

        assertEquals(log100b + log100a + logv11 + log110, baos.toString(UTF_8));
    }

    @Test
    void testThatLogsNotMatchingRegexAreExcluded() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*-1.*"));
        logReader.writeLogs(baos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), 100, Optional.empty());

        assertEquals(log101 + logv11, baos.toString(UTF_8));
    }

    @EnabledIf("hasZstdcat")
    @Test
    void testZippedStreaming() {
        ByteArrayOutputStream zippedBaos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(zippedBaos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), 100, Optional.empty());

        assertEquals(log101 + log100b + log100a + logv11 + log110 + log200 + logv, zippedBaos.toString(UTF_8));
    }

    @Test
    void logsForSingeNodeIsRetrieved() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), 100, Optional.of("node2.com"));

        assertEquals(log101 + log100b + log100a + log200, baos.toString(UTF_8));
    }

    @Test
    void logsLimitedToMaxLines() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LogReader logReader = new LogReader(logDirectory, Pattern.compile(".*"));
        logReader.writeLogs(baos, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(2)), 2, Optional.of("node2.com"));

        assertEquals(log101 + log100b, baos.toString(UTF_8));
    }

    private byte[] compress1(String input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream zip = new GZIPOutputStream(baos);
        zip.write(input.getBytes());
        zip.close();
        return baos.toByteArray();
    }

    private byte[] compress2(String input) {
        byte[] data = input.getBytes();
        return new ZstdCompressor().compress(data, 0, data.length);
    }

}
