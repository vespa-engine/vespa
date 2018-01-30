// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author hakonhall
 */
public interface ChildProcess extends AutoCloseable {
    String commandLine();
    ChildProcess waitForTermination();
    int exitValue();
    ChildProcess throwIfFailed();
    String getUtf8Output();

    /**
     * Returns an UnexpectedOutputException that includes a snippet of the output in the message.
     *
     * @param problem Problem description, e.g. "Output is not of the form ^NAME=VALUE$"
     */
    UnexpectedOutputException newUnexpectedOutputException(String problem);

    /**
     * Only call this if process was spawned under the assumption the program had no side
     * effects (see Command::spawnProgramWithoutSideEffects).  If it is determined later
     * that the program did in fact have side effects (modified system), this method can
     * be used to log that fact. Alternatively, call TaskContext::logSystemModification
     * directly.
     */
    void logAsModifyingSystemAfterAll(Logger logger);

    @Override
    void close();

    // For testing only
    Path getProcessOutputPath();
}
