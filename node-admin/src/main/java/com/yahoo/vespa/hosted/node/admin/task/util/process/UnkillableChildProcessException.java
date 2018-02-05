// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

import java.time.Duration;

/**
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class UnkillableChildProcessException extends ChildProcessException {
    public UnkillableChildProcessException(Duration waitForSigTerm,
                                           Duration waitForSigKill,
                                           String commandLine,
                                           String possiblyHugeOutput) {
        super("did not terminate even after SIGTERM, +" + waitForSigTerm +
                ", SIGKILL, and +" + waitForSigKill,
                commandLine,
                possiblyHugeOutput);
    }
}
