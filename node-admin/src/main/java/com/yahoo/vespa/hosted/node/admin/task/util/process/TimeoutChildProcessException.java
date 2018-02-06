// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.time.Duration;

/**
 * Exception thrown when a child process has taken too long to terminate, in case it has been
 * forcibly killed.
 *
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class TimeoutChildProcessException extends ChildProcessException {
    TimeoutChildProcessException(Duration timeout, String commandLine, String possiblyHugeOutput) {
        super("timed out after " + timeout, commandLine, possiblyHugeOutput);
    }
}
