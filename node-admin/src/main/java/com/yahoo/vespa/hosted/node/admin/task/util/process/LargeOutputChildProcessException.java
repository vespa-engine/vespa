// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

/**
 * Exception thrown if the output of the child process is larger than the maximum limit.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class LargeOutputChildProcessException extends ChildProcessException {
    LargeOutputChildProcessException(long maxFileSize, String commandLine, String possiblyHugeOutput) {
        super("output more than " + maxFileSize + " bytes", commandLine, possiblyHugeOutput);
    }
}
