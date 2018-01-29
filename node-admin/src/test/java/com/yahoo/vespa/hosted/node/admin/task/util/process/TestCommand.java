// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestCommand extends Command {
    private final String expectedCommandLine;
    private final ChildProcess childProcess;

    private boolean invoked = false;

    public TestCommand(TaskContext context,
                       String expectedCommandLine,
                       int exitValue,
                       String out) {
        super(context);
        this.expectedCommandLine = expectedCommandLine;
        this.childProcess = new ChildProcess() {
            @Override
            public ChildProcess waitForTermination() {
                return this;
            }

            @Override
            public int exitValue() {
                return exitValue;
            }

            @Override
            public ChildProcess throwIfFailed() {
                if (exitValue != 0) {
                    throw new CommandException("exited with " + exitValue);
                }
                return this;
            }

            @Override
            public String getUtf8Output() {
                return out;
            }

            @Override
            public void close() { }

            @Override
            public Path getProcessOutputPath() { return null; }

            @Override
            public void logAsModifyingSystemAfterAll(Logger logger) { }

            @Override
            public String commandLine() {
                return "program";
            }
        };
    }

    @Override
    public ChildProcess spawn(Logger commandLogger) {
        assertFalse(invoked);
        invoked = true;

        assertEquals(expectedCommandLine, commandLine());

        return childProcess;
    }

    public void verifyInvocation() {
        if (!invoked) {
            throw new IllegalStateException("Command not invoked: " + expectedCommandLine);
        }
    }
}
