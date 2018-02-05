// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.time.TestTimer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProcessFactoryImplTest {
    private final ProcessStarter starter = mock(ProcessStarter.class);
    private final TestTimer timer = new TestTimer();
    private final ProcessFactoryImpl processFactory = new ProcessFactoryImpl(starter, timer);

    @Test
    public void testSpawn() {
        CommandLine commandLine = mock(CommandLine.class);
        when(commandLine.getArguments()).thenReturn(Arrays.asList("program"));
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
}