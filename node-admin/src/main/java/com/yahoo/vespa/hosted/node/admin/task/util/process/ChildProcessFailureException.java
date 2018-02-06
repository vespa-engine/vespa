// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

/**
 * The child process terminated with a non-zero exit code.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class ChildProcessFailureException extends ChildProcessException {
    ChildProcessFailureException(int exitCode, String commandLine, String possiblyHugeOutput) {
        super("terminated with exit code " + exitCode, commandLine, possiblyHugeOutput);
    }
}
