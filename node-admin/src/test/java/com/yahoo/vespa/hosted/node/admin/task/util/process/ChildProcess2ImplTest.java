// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class ChildProcess2ImplTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final Timer timer = mock(Timer.class);
    private final CommandLine commandLine = mock(CommandLine.class);
    private final ProcessApi2 processApi = mock(ProcessApi2.class);
    private Path temporaryFile;

    @BeforeEach
    public void setUp() throws IOException {
        temporaryFile = Files.createTempFile(fileSystem.getPath("/"), "", "");
    }

    @Test
    void testSuccess() throws Exception {
        when(commandLine.getTimeout()).thenReturn(Duration.ofHours(1));
        when(commandLine.getMaxOutputBytes()).thenReturn(10L);
        when(commandLine.getOutputEncoding()).thenReturn(StandardCharsets.UTF_8);
        when(commandLine.getSigTermGracePeriod()).thenReturn(Duration.ofMinutes(2));
        when(commandLine.getSigKillGracePeriod()).thenReturn(Duration.ofMinutes(3));
        when(commandLine.toString()).thenReturn("program arg");

        when(timer.currentTime()).thenReturn(
                Instant.ofEpochMilli(1),
                Instant.ofEpochMilli(2));

        when(processApi.waitFor(anyLong(), any())).thenReturn(true);

        try (ChildProcess2Impl child =
                new ChildProcess2Impl(commandLine, processApi, temporaryFile, timer)) {
            child.waitForTermination();
        }
    }

    @Test
    void testTimeout() throws Exception {
        when(commandLine.getTimeout()).thenReturn(Duration.ofSeconds(1));
        when(commandLine.getMaxOutputBytes()).thenReturn(10L);
        when(commandLine.getOutputEncoding()).thenReturn(StandardCharsets.UTF_8);
        when(commandLine.getSigTermGracePeriod()).thenReturn(Duration.ofMinutes(2));
        when(commandLine.getSigKillGracePeriod()).thenReturn(Duration.ofMinutes(3));
        when(commandLine.toString()).thenReturn("program arg");

        when(timer.currentTime()).thenReturn(
                Instant.ofEpochSecond(0),
                Instant.ofEpochSecond(2));

        when(processApi.waitFor(anyLong(), any())).thenReturn(true);

        try (ChildProcess2Impl child =
                new ChildProcess2Impl(commandLine, processApi, temporaryFile, timer)) {
            try {
                child.waitForTermination();
                fail();
            } catch (TimeoutChildProcessException e) {
                assertEquals(
                        "Command 'program arg' timed out after PT1S: stdout/stderr: ''",
                        e.getMessage());
            }
        }
    }

    @Test
    void testMaxOutputBytes() throws Exception {
        when(commandLine.getTimeout()).thenReturn(Duration.ofSeconds(1));
        when(commandLine.getMaxOutputBytes()).thenReturn(10L);
        when(commandLine.getOutputEncoding()).thenReturn(StandardCharsets.UTF_8);
        when(commandLine.getSigTermGracePeriod()).thenReturn(Duration.ofMinutes(2));
        when(commandLine.getSigKillGracePeriod()).thenReturn(Duration.ofMinutes(3));
        when(commandLine.toString()).thenReturn("program arg");

        when(timer.currentTime()).thenReturn(
                Instant.ofEpochMilli(0),
                Instant.ofEpochMilli(1));

        when(processApi.waitFor(anyLong(), any())).thenReturn(true);

        Files.writeString(temporaryFile, "1234567890123");

        try (ChildProcess2Impl child =
                new ChildProcess2Impl(commandLine, processApi, temporaryFile, timer)) {
            try {
                child.waitForTermination();
                fail();
            } catch (LargeOutputChildProcessException e) {
                assertEquals(
                        "Command 'program arg' output more than 13 bytes: stdout/stderr: '1234567890123'",
                        e.getMessage());
            }
        }
    }

    @Test
    void testUnkillable() throws Exception {
        when(commandLine.getTimeout()).thenReturn(Duration.ofSeconds(1));
        when(commandLine.getMaxOutputBytes()).thenReturn(10L);
        when(commandLine.getOutputEncoding()).thenReturn(StandardCharsets.UTF_8);
        when(commandLine.getSigTermGracePeriod()).thenReturn(Duration.ofMinutes(2));
        when(commandLine.getSigKillGracePeriod()).thenReturn(Duration.ofMinutes(3));
        when(commandLine.toString()).thenReturn("program arg");

        when(timer.currentTime()).thenReturn(
                Instant.ofEpochMilli(0),
                Instant.ofEpochMilli(1));

        when(processApi.waitFor(anyLong(), any())).thenReturn(false);

        Files.writeString(temporaryFile, "1234567890123");

        try (ChildProcess2Impl child =
                new ChildProcess2Impl(commandLine, processApi, temporaryFile, timer)) {
            try {
                child.waitForTermination();
                fail();
            } catch (UnkillableChildProcessException e) {
                assertEquals(
                        "Command 'program arg' did not terminate even after SIGTERM, +PT2M, SIGKILL, and +PT3M: stdout/stderr: '1234567890123'",
                        e.getMessage());
            }
        }
    }
}