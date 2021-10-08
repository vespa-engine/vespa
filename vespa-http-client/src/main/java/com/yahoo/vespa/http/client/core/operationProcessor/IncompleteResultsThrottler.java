// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.core.ThrottlePolicy;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adjusts in-flight operations based on throughput. It will walk the graph and try to find
 * local optimum.
 *
 * It looks at the throughput, adjust max in-flight based on the previous throughput and settings.
 *
 * In the beginning it moves faster, and then stabilizes.
 *
 * It will wait a bit after adjusting before it starts to sample, since there is a latency between adjustment
 * and result.
 *
 * There are several mechanisms to reduce impact of several clients running in parallel. The window size has a
 * random part, and the wait time before sampling after adjustment has a random part as well.
 *
 * To avoid running wild with large values of max-in flight, it is tuned to stay on the smaller part, and
 * rather reduce max-in flight than to have a too large value.
 *
 * In case the where the queue is moved to minimum size, it will now and then increase queue size to get
 * more sample data and possibly grow size.
 *
 * Class is fully thread safe, i.e. all public methods are thread safe.
 *
 * @author dybis
 */
public class IncompleteResultsThrottler {

    private final ConcurrentDocumentOperationBlocker blocker = new ConcurrentDocumentOperationBlocker();
    private final int maxInFlightValue;
    private final int minInFlightValue;
    private final ThrottlePolicy policy;

    // 9-11 seconds with some randomness to avoid fully synchronous feeders.
    public final long phaseSizeMs = 9000 + (ThreadLocalRandom.current().nextInt() % 2000);
    private final Clock clock;

    private final Object monitor = new Object();
    private long sampleStartTimeMs = 0;
    private int previousNumOk = 0;
    private int previousMaxInFlight = 0;
    private int stabilizingPhasesLeft = 0;
    private int adjustCycleCount = 0;
    private int maxInFlightNow;
    private int numOk = 0;
    private int minWindowSizeCounter = 0;
    private int minPermitsAvailable = 0;

    protected static int INITIAL_MAX_IN_FLIGHT_VALUE = 200;
    protected static int SECOND_MAX_IN_FLIGHT_VALUE = 270;
    private StringBuilder debugMessage = new StringBuilder();

    /**
     * Creates the throttler.
     *
     * @param minInFlightValue the throttler will never throttle beyond this limit.
     * @param maxInFlightValue the throttler will never throttle above this limit. If zero, no limit.
     * @param clock use to calculate window size. Can be null if minWindowSize and maxInFlightValue are equal.
     * @param policy is the algorithm for finding next value of the number of in-flight documents operations.
     */
    public IncompleteResultsThrottler(int minInFlightValue, int maxInFlightValue, Clock clock, ThrottlePolicy policy) {
        this.maxInFlightValue = maxInFlightValue == 0 ? Integer.MAX_VALUE : maxInFlightValue;
        this.minInFlightValue = minInFlightValue == 0 ? this.maxInFlightValue : minInFlightValue;
        this.policy = policy;
        this.clock = clock;
        if (minInFlightValue != maxInFlightValue) {
            this.sampleStartTimeMs = clock.millis();
        }
        setNewSemaphoreSize(INITIAL_MAX_IN_FLIGHT_VALUE);
    }

    public int availableCapacity() {
        return blocker.availablePermits();
    }

    public void operationStart() {
        try {
            blocker.startOperation();
        } catch (InterruptedException e) {
            // Ignore
        }
        if (maxInFlightValue != minInFlightValue) {
            synchronized (monitor) {
                adjustThrottling();
            }
        }
    }

    public String getDebugMessage() {
        synchronized (monitor) {
            return debugMessage.toString();
        }
    }

    public void resultReady(boolean success) {
        blocker.operationDone();
        if (!success) {
            return;
        }
        synchronized (monitor) {
            numOk++;
            minPermitsAvailable = Math.min(minPermitsAvailable, blocker.availablePermits());
        }
    }

    // Only for testing
    protected int waitingThreads() {
        synchronized (monitor) {
            return maxInFlightNow - blocker.availablePermits();
        }
    }

    private double getCeilingDifferencePerformance(int adjustCycle) {
        // We want larger adjustments in the early phase.
        if (adjustCycle > 10) {
            return 0.7;
        }
        return 1.2;
    }

    private void adjustCycle() {
        adjustCycleCount++;
        stabilizingPhasesLeft = adjustCycleCount < 5 ? 1 : 2 + ThreadLocalRandom.current().nextInt() % 2;

        double maxPerformanceChange = getCeilingDifferencePerformance(adjustCycleCount);
        boolean messagesQueued = minPermitsAvailable < 2;

        int newMaxInFlight = policy.calcNewMaxInFlight(
                maxPerformanceChange, numOk, previousNumOk, previousMaxInFlight, maxInFlightNow, messagesQueued);
        debugMessage = new StringBuilder();
        debugMessage.append("previousMaxInFlight: " + previousMaxInFlight
                + " maxInFlightNow: " + maxInFlightNow
                + " numOk: " + numOk + " " + " previousOk: " + previousNumOk
                + " new size is: " + newMaxInFlight);
        previousMaxInFlight = maxInFlightNow;
        previousNumOk = numOk;

        setNewSemaphoreSize(adjustCycleCount == 1 ? SECOND_MAX_IN_FLIGHT_VALUE : newMaxInFlight);
    }

    private void adjustThrottling() {
        if (clock.millis() < sampleStartTimeMs + phaseSizeMs) return;

        sampleStartTimeMs += phaseSizeMs;

        if (stabilizingPhasesLeft-- == 0) {
            adjustCycle();
        }
        numOk = 0;
        this.minPermitsAvailable = maxInFlightNow;
    }

    private int tryBoostingSizeIfMinValueOverSeveralCycles(final int size) {
        if (size <= minInFlightValue) {
            minWindowSizeCounter++;
        } else {
            minWindowSizeCounter = 0;
        }
        if (minWindowSizeCounter == 4) {
            debugMessage.append(" (inc max in flight to get more data)");
            minWindowSizeCounter = 0;
            return size + 10;
        }
        return size;

    }

    private void setNewSemaphoreSize(final int size) {
        maxInFlightNow =
                Math.max(minInFlightValue, Math.min(
                        tryBoostingSizeIfMinValueOverSeveralCycles(size), maxInFlightValue));
        blocker.setMaxConcurrency(maxInFlightNow);
    }

}
