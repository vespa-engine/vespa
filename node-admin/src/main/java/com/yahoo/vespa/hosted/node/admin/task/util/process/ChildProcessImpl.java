// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Represents a forked child process that still exists or has terminated.
 *
 * @author hakonhall
 */
public class ChildProcessImpl implements ChildProcess {
    private final TaskContext taskContext;
    private final Process process;
    private final Path processOutputPath;
    private final String commandLine;

    ChildProcessImpl(TaskContext taskContext,
                     Process process,
                     Path processOutputPath,
                     String commandLine) {
        this.taskContext = taskContext;
        this.process = process;
        this.processOutputPath = processOutputPath;
        this.commandLine = commandLine;
    }

    @Override
    public String commandLine() {
        return commandLine;
    }

    public String getUtf8Output() {
        waitForTermination();
        return new UnixPath(processOutputPath).readUtf8File();
    }

    public ChildProcessImpl waitForTermination() {
        while (true) {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                // ignoring
                continue;
            }

            return this;
        }
    }

    public int exitValue() {
        waitForTermination();
        return process.exitValue();
    }

    public ChildProcess throwIfFailed() {
        waitForTermination();
        if (process.exitValue() != 0) {
            throw new CommandException("Execution of program [" + commandLine +
                    "] failed, stdout/stderr was: <" + suffixOfOutputForLog() + ">");
        }

        return this;
    }

    private String suffixOfOutputForLog() {
        String output = getUtf8Output();

        final int maxTrailingChars = 300;
        if (output.length() <= maxTrailingChars) {
            return output;
        }

        int numSkippedChars = output.length() - maxTrailingChars;
        output = output.substring(numSkippedChars);
        return "[" + numSkippedChars + " chars omitted]..." + output;
    }

    @Override
    public void logAsModifyingSystemAfterAll(Logger logger) {
        taskContext.logSystemModification(logger, "Executed command: " + commandLine);
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            process.destroyForcibly();
            waitForTermination();
        }
        processOutputPath.toFile().delete();
    }

    @Override
    public Path getProcessOutputPath() {
        return processOutputPath;
    }
}
