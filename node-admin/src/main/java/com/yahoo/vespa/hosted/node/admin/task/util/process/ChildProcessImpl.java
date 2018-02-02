// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Represents a forked child process that still exists or has terminated.
 *
 * @author hakonhall
 */
public class ChildProcessImpl implements ChildProcess {
    private final TaskContext taskContext;
    private final ProcessApi process;
    private final String commandLine;

    private Optional<String> utf8OutputCache = Optional.empty();

    ChildProcessImpl(TaskContext taskContext,
                     Process process,
                     Path processOutputPath,
                     String commandLine) {
        this(taskContext, new ProcessApiImpl(process, processOutputPath), commandLine);
    }

    ChildProcessImpl(TaskContext taskContext,
                     ProcessApi process,
                     String commandLine) {
        this.taskContext = taskContext;
        this.process = process;
        this.commandLine = commandLine;
    }

    @Override
    public String commandLine() {
        return commandLine;
    }

    public String getUtf8Output() {
        if (!utf8OutputCache.isPresent()) {
            waitForTermination();
            utf8OutputCache = Optional.of(process.getUtf8Output());
        }

        return utf8OutputCache.get();
    }

    public ChildProcessImpl waitForTermination() {
        process.waitForTermination();
        return this;
    }

    public int exitValue() {
        waitForTermination();
        return process.exitCode();
    }

    public ChildProcess throwIfFailed() {
        waitForTermination();
        int exitCode = process.exitCode();
        if (exitCode != 0) {
            String message = ErrorMessageFormatter.createSnippetForTerminatedProcess(
                    "terminated with non-zero exit code " + exitCode,
                    this);
            throw new CommandException(message);
        }

        return this;
    }

    @Override
    public void logAsModifyingSystemAfterAll(Logger logger) {
        taskContext.recordSystemModification(logger, "Executed command: " + commandLine);
    }

    @Override
    public void close() {
        process.close();
    }

    @Override
    public Path getProcessOutputPath() {
        return process.getProcessOutputPath();
    }
}
