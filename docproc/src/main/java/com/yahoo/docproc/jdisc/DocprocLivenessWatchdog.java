// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches whether document processing is making progress, by periodically sampling how many
 * requests have been submitted for processing versus how many have completed. If requests are
 * outstanding but none complete for a sustained number of samples, this is the signature of a
 * deadlock or CPU starvation among the container's request processing threads, and is reported
 * to a {@link LivenessSink} so the container's health status reflects it. Progress resuming for
 * a sustained number of samples clears the report again.
 *
 * Hysteresis (requiring several consecutive samples in a row) avoids reacting to a single slow
 * tick, and avoids flapping once a stall has been reported.
 *
 * @author hmusum
 */
class DocprocLivenessWatchdog implements Runnable {

    interface LivenessSink {
        void setLivenessOk(boolean ok);
    }

    private static final Logger log = Logger.getLogger(DocprocLivenessWatchdog.class.getName());

    private final LongSupplier submittedCount;
    private final LongSupplier completedCount;
    private final LivenessSink livenessSink;
    private final int samplesToTrip;

    private long lastCompleted = -1;
    private int consecutiveStalledSamples = 0;
    private int consecutiveHealthySamples = 0;
    private boolean stalled = false;

    DocprocLivenessWatchdog(LongSupplier submittedCount, LongSupplier completedCount, LivenessSink livenessSink,
                            double sampleIntervalSeconds, double stalledThresholdSeconds) {
        this(submittedCount, completedCount, livenessSink,
             Math.max(1, (int) Math.round(stalledThresholdSeconds / sampleIntervalSeconds)));
    }

    /** For testing: trip/clear after exactly this many consecutive samples, rather than deriving it from seconds. */
    DocprocLivenessWatchdog(LongSupplier submittedCount, LongSupplier completedCount, LivenessSink livenessSink,
                            int samplesToTrip) {
        this.submittedCount = submittedCount;
        this.completedCount = completedCount;
        this.livenessSink = livenessSink;
        this.samplesToTrip = samplesToTrip;
    }

    @Override
    public void run() {
        try {
            sample();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Exception in docproc liveness watchdog", e);
        }
    }

    /** Takes one sample and updates liveness state accordingly. Package-private for testing. */
    void sample() {
        long completed = completedCount.getAsLong();
        long outstanding = submittedCount.getAsLong() - completed;
        boolean progressed = lastCompleted == -1 || completed != lastCompleted;
        lastCompleted = completed;

        if (outstanding > 0 && !progressed) {
            consecutiveStalledSamples++;
            consecutiveHealthySamples = 0;
        } else {
            consecutiveHealthySamples++;
            consecutiveStalledSamples = 0;
        }

        if ( ! stalled && consecutiveStalledSamples >= samplesToTrip) {
            stalled = true;
            long outstandingAtTrip = outstanding;
            log.log(Level.WARNING, () -> "Document processing appears stalled: " + outstandingAtTrip +
                                          " request(s) outstanding with no completions for " + consecutiveStalledSamples +
                                          " consecutive samples. Reporting container as unhealthy.");
            livenessSink.setLivenessOk(false);
        } else if (stalled && consecutiveHealthySamples >= samplesToTrip) {
            stalled = false;
            log.log(Level.INFO, "Document processing has resumed making progress. Reporting container as healthy again.");
            livenessSink.setLivenessOk(true);
        }
    }

    /** For testing. */
    boolean isStalled() { return stalled; }

}
