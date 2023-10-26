// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.protect;

import com.yahoo.protect.Process;

/**
 * An injectable terminator of the Java VM.
 * Components that encounter conditions where the VM should be terminated should
 * request an instance of this injected. That makes termination testable
 * as tests can create subclasses of this which register the termination request
 * rather than terminating.
 *
 * @author bratseth
 */
public class ProcessTerminator {

    /** Logs and dies without taking a thread dump */
    public void logAndDie(String message) {
        logAndDie(message, false);
    }

    /**
     * Logs and dies
     *
     * @param dumpThreads if true the stack trace of all threads is dumped to the
     *                   log with level info before shutting down
     */
    public void logAndDie(String message, boolean dumpThreads) {
        Process.logAndDie(message, dumpThreads);
    }

}
