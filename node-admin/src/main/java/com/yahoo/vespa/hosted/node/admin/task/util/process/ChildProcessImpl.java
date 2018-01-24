// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.Path;

/**
 * Represents a forked child process that still exists or has terminated.
 */
public class ChildProcessImpl implements ChildProcess {
    private final Process process;
    private final Path processOutputPath;
    private final String commandLine;

    ChildProcessImpl(Process process, Path processOutputPath, String commandLine) {
        this.process = process;
        this.processOutputPath = processOutputPath;
        this.commandLine = commandLine;
    }

    public String getUtf8Output() {
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
        return process.exitValue();
    }

    public ChildProcess throwIfFailed() {
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
