// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.protect;


import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A class for interacting with the global state of the running VM.
 *
 * @author Steinar Knutsen
 */
public final class Process {

    private static final AtomicBoolean alreadyShuttingDown = new AtomicBoolean(false);
    private static final AtomicBoolean busyDumpingThreads = new AtomicBoolean(false);

    private static final Logger log = Logger.getLogger(Process.class.getName());

    /** Die with a message, without dumping thread state */
    public static void logAndDie(String message) {
        logAndDie(message, null);
    }

    /** Die with a message, optionally dumping thread state */
    public static void logAndDie(String message, boolean dumpThreads) {
        logAndDie(message, null, dumpThreads);
    }

    /** Die with a message containing an exception, without dumping thread state */
    public static void logAndDie(String message, Throwable thrown) {
        logAndDie(message, thrown, false);
    }

    /**
     * Log message as severe error, then forcibly exit runtime, without running
     * exit handlers or otherwise waiting for cleanup.
     *
     * @param message message to log before exit
     * @param thrown the throwable that caused the application to exit.
     * @param dumpThreads if true the stack trace of all threads is dumped to the
     *                   log with level info before shutting down
     */
    public static void logAndDie(String message, Throwable thrown, boolean dumpThreads) {
        boolean shutDownInProgress = alreadyShuttingDown.getAndSet(true);
        try {
            if (thrown != null) {
                log.log(Level.SEVERE, message, thrown);
            } else {
                log.log(Level.SEVERE, message);
            }
            log.log(Level.INFO, "About to shut down.");
            if (dumpThreads) {
                dumpThreads();
            }
        } finally {
            if ( ! shutDownInProgress ) {
                try {
                    Runtime.getRuntime().halt(1);
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Runtime.halt rejected. Throwing an error.");
                    throw new ShutdownError("Shutdown requested, but failed to shut down");
                }
            } else {
                log.log(Level.WARNING, "Shutdown already in progress. Will just let death come upon us normally.");
            }
        }
    }


    public static void dumpThreads() {
        boolean alreadyDumpingThreads = busyDumpingThreads.getAndSet(true);
        if ( ! alreadyDumpingThreads ) {
            try {
                log.log(Level.INFO, "Commencing full thread dump for diagnosis.");
                Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
                for (Map.Entry<Thread, StackTraceElement[]> e : allStackTraces.entrySet()) {
                    Thread t = e.getKey();
                    StackTraceElement[] stack = e.getValue();
                    StringBuilder forOneThread = new StringBuilder();
                    forOneThread.append("Stack for thread: ").append(t.getName()).append(": ");
                    for (StackTraceElement s : stack) {
                        forOneThread.append('\n').append(s.toString());
                    }
                    log.log(Level.INFO, forOneThread.toString());
                }
                log.log(Level.INFO, "End of diagnostic thread dump.");
            } catch (Exception e) {
                // just give up...
            }
            busyDumpingThreads.set(false);
        } else {
            log.log(Level.WARNING, "Thread dump already in progress. Skipping it.");
        }
    }

    @SuppressWarnings("serial")
    public static class ShutdownError extends Error {

        public ShutdownError(String message) {
            super(message);
        }

    }

}
