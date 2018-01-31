// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import org.junit.Test;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CommandTest {
    @Test
    public void testCommand() {
        TaskContext taskContext = mock(TaskContext.class);
        Logger logger = mock(Logger.class);

        Command command = new Command(taskContext).add("bash", "-c", "ls /bin/bash");
        Path outputFile;
        // Assumes /bin/bash exists on all hosts running this test.
        try (ChildProcess childProcess = command.spawn(logger)) {
            verify(taskContext).logSystemModification(eq(logger), any());

            outputFile = childProcess.getProcessOutputPath();
            int exitValue = childProcess.waitForTermination().exitValue();
            assertEquals(0, exitValue);
            childProcess.throwIfFailed();
            String output = childProcess.getUtf8Output().trim();
            assertEquals("/bin/bash", output);
            assertTrue(outputFile.toFile().exists());
        }

        assertFalse(outputFile.toFile().exists());
    }

    @Test(expected = UncheckedIOException.class)
    public void noSuchProgram() {
        TaskContext taskContext = mock(TaskContext.class);
        Logger logger = mock(Logger.class);

        Command command = new Command(taskContext).add("thisprogRamDoes-not-exist");
        try (ChildProcess childProcess = command.spawn(logger)) {
            dummyToRemoveWarning(childProcess);
        }

        fail();
    }

    private void dummyToRemoveWarning(ChildProcess childProcess) { }

    @Test
    public void argumentEscape() {
        TaskContext taskContext = mock(TaskContext.class);
        Command command = new Command(taskContext).add("b", "\" \\ foo", "bar x", "");
        assertEquals("b \"\\\" \\\\ foo\" \"bar x\" \"\"", command.commandLine());
    }

    @Test
    public void failingProgram() {
        TaskContext taskContext = mock(TaskContext.class);
        Logger logger = mock(Logger.class);

        Command command = new Command(taskContext)
                .add("bash", "-c", "echo foo; echo bar >&2; exit 1");
        Path outputFile;
        try (ChildProcess childProcess = command.spawn(logger)) {
            try {
                childProcess.waitForTermination().throwIfFailed();
                fail();
            } catch (CommandException e) {
                assertEquals("Command 'bash -c \"echo foo; echo bar >&2; exit 1\"' terminated with non-zero exit code 1: stdout/stderr: 'foo\n" +
                                "bar\n" +
                                "'",
                        e.getMessage());
            }
        }

    }
}