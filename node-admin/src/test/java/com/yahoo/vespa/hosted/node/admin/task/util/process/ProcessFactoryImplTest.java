// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.jdisc.test.TestTimer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProcessFactoryImplTest {
    private final ProcessStarter starter = mock(ProcessStarter.class);
    private final TestTimer timer = new TestTimer();
    private final ProcessFactoryImpl processFactory = new ProcessFactoryImpl(starter, timer);

    @Test
    void testSpawn() {
        CommandLine commandLine = mock(CommandLine.class);
        when(commandLine.getArguments()).thenReturn(List.of("program"));
        when(commandLine.getRedirectStderrToStdoutInsteadOfDiscard()).thenReturn(true);
        when(commandLine.programName()).thenReturn("program");
        Path outputPath;
        try (ChildProcess2Impl child = processFactory.spawn(commandLine)) {
            outputPath = child.getOutputPath();
            assertTrue(Files.exists(outputPath));
            assertEquals("rw-------", new UnixPath(outputPath).getPermissions());
            ArgumentCaptor<ProcessBuilder> processBuilderCaptor =
                    ArgumentCaptor.forClass(ProcessBuilder.class);
            verify(starter).start(processBuilderCaptor.capture());
            ProcessBuilder processBuilder = processBuilderCaptor.getValue();
            assertTrue(processBuilder.redirectErrorStream());
            ProcessBuilder.Redirect redirect = processBuilder.redirectOutput();
            assertEquals(ProcessBuilder.Redirect.Type.WRITE, redirect.type());
            assertEquals(outputPath.toFile(), redirect.file());
        }

        assertFalse(Files.exists(outputPath));
    }

    @Test
    void testSpawnWithPersistentOutputFile() {

        class TemporaryFile implements AutoCloseable {
            private final Path path;

            private TemporaryFile() {
                String outputFileName = ProcessFactoryImplTest.class.getSimpleName() + "-temporary-test-file.out";
                FileAttribute<Set<PosixFilePermission>> fileAttribute = PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------"));
                path = uncheck(() -> Files.createTempFile(outputFileName, ".out", fileAttribute));
            }

            @Override
            public void close() {
                uncheck(() -> Files.deleteIfExists(path));
            }
        }

        try (TemporaryFile outputPath = new TemporaryFile()) {
            CommandLine commandLine = mock(CommandLine.class);
            when(commandLine.getArguments()).thenReturn(List.of("program"));
            when(commandLine.programName()).thenReturn("program");
            when(commandLine.getOutputFile()).thenReturn(Optional.of(outputPath.path));
            try (ChildProcess2Impl child = processFactory.spawn(commandLine)) {
                assertEquals(outputPath.path, child.getOutputPath());
                assertTrue(Files.exists(outputPath.path));
                assertEquals("rw-------", new UnixPath(outputPath.path).getPermissions());
            }

            assertTrue(Files.exists(outputPath.path));
        }

    }

}