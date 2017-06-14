// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.protect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.concurrent.ThreadLocalDirectory;
import com.yahoo.log.LogLevel;
import com.yahoo.protect.Process;

/**
 * Watchdog for a frozen process, too many timeouts, etc.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @deprecated this is not in use and will be removed in the next major release
 */
// TODO: Remove on Vespa 7
@Deprecated
class Watchdog extends TimerTask {

    public static final String FREEZEDETECTOR_DISABLE = "vespa.freezedetector.disable";
    Logger log = Logger.getLogger(Watchdog.class.getName());
    private long lastRun = 0L;
    private long lastQpsCheck = 0L;
    // Local copy to avoid ever _reading_ the volatile version
    private boolean breakdownCopy = false;
    private volatile boolean breakdown;
    // The fraction of queries which must time out to view the QRS as being
    // in breakdown
    private final double timeoutThreshold;
    // The minimal QPS to care about timeoutThreshold
    private final int minimalQps;
    private final boolean disableSevereBreakdownCheck;
    private final List<ThreadLocalDirectory<TimeoutRate, Boolean>> timeoutRegistry = new ArrayList<>();
    private final boolean shutdownIfFrozen;

    Watchdog(double timeoutThreshold, int minimalQps, boolean shutdownIfFrozen) {
        this.timeoutThreshold = timeoutThreshold;
        this.minimalQps = minimalQps;
        if (System.getProperty(FREEZEDETECTOR_DISABLE) != null) {
            disableSevereBreakdownCheck = true;
        } else {
            disableSevereBreakdownCheck = false;
        }
        this.shutdownIfFrozen = shutdownIfFrozen;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        if (lastRun != 0L) {
            severeBreakdown(now);
            queryTimeouts(now);
        } else {
            lastQpsCheck = now;
        }
        lastRun = now;
    }

    private void severeBreakdown(final long now) {
        if (disableSevereBreakdownCheck) {
            return;
        }
        if (now - lastRun < 5000L) {
            return;
        }

        threadStackMessage();

        if (shutdownIfFrozen) {
            Process.logAndDie("Watchdog timer meant to run ten times per second"
                    + " not run for five seconds or more."
                    + " Assuming severe failure or overloaded node, shutting down container.");
        } else {
            log.log(LogLevel.ERROR,
                    "A watchdog meant to run 10 times a second has not been invoked for 5 seconds."
                            + " This usually means this machine is swapping or otherwise severely overloaded.");
        }
    }

    private void threadStackMessage() {
        log.log(LogLevel.INFO, "System seems unresponsive, performing full thread dump for diagnostics.");
        threadDump();
        log.log(LogLevel.INFO, "End of diagnostic thread dump.");
    }

    private void threadDump() {
        try {
            Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> e : allStackTraces.entrySet()) {
                Thread t = e.getKey();
                StackTraceElement[] stack = e.getValue();
                StringBuilder forOneThread = new StringBuilder();
                int initLen;
                forOneThread.append("Stack for thread: ").append(t.getName()).append(": ");
                initLen = forOneThread.length();
                for (StackTraceElement s : stack) {
                    if (forOneThread.length() > initLen) {
                        forOneThread.append(" ");
                    }
                    forOneThread.append(s.toString());
                }
                log.log(LogLevel.INFO, forOneThread.toString());
            }
        } catch (Exception e) {
            // just give up...
        }
    }

    private void queryTimeouts(final long now) {
        // only check query timeout every 10s
        if (now - lastQpsCheck < 10000L) {
            return;
        } else {
            lastQpsCheck = now;
        }

        final TimeoutRate globalState = new TimeoutRate();
        synchronized (timeoutRegistry) {
            for (ThreadLocalDirectory<TimeoutRate, Boolean> timeouts : timeoutRegistry) {
                final List<TimeoutRate> threadStates = timeouts.fetch();
                for (final TimeoutRate t : threadStates) {
                    globalState.merge(t);
                }
            }
        }
        if (globalState.timeoutFraction() > timeoutThreshold && globalState.getTotal() > (10 * minimalQps)) {
            setBreakdown(true);
            log.log(Level.WARNING, "Too many queries timed out. Assuming container is in breakdown.");
        } else {
            if (!breakdown()) {
                return;
            }
            setBreakdown(false);
            log.log(Level.WARNING, "Fewer queries timed out. Assuming container is no longer in breakdown.");
        }
    }

    private void setBreakdown(final boolean state) {
        breakdown = state;
        breakdownCopy = state;
    }

    private boolean breakdown() {
        return breakdownCopy;
    }

    boolean isBreakdown() {
        return breakdown;
    }

    void addTimeouts(ThreadLocalDirectory<TimeoutRate, Boolean> t) {
        synchronized (timeoutRegistry) {
            timeoutRegistry.add(t);
        }
    }

    void removeTimeouts(ThreadLocalDirectory<TimeoutRate, Boolean> timeouts) {
        synchronized (timeoutRegistry) {
            timeoutRegistry.remove(timeouts);
        }
    }

}
