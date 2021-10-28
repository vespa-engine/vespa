// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

/**
 * Very basic sampling strategy which allows for sampling N requests within a fixed
 * time window. No attempt is made to distribute the samples evenly within the time
 * period; this is on a first-come, first-serve basis.
 */
public class MaxSamplesPerPeriod implements SamplingStrategy {

    private final MonotonicNanoClock nanoClock;
    private final long maxSamplesPerPeriod;
    private final long periodLengthInNanos;
    private long currentSamplingPeriod = 0;
    private long samplesInCurrentPeriod = 0;

    public MaxSamplesPerPeriod(MonotonicNanoClock nanoClock, long periodLengthInNanos, long maxSamplesPerPeriod) {
        this.nanoClock = nanoClock;
        this.periodLengthInNanos = periodLengthInNanos;
        this.maxSamplesPerPeriod = maxSamplesPerPeriod;
    }

    public static MaxSamplesPerPeriod withSteadyClock(long periodLengthInNanos, long maxSamplesPerPeriod) {
        // We make a reasonable assumption that System.nanoTime uses the underlying steady clock.
        return new MaxSamplesPerPeriod(System::nanoTime, periodLengthInNanos, maxSamplesPerPeriod);
    }

    @Override
    public boolean shouldSample() {
        long now = nanoClock.nanoTimeNow();
        long period = now / periodLengthInNanos;
        synchronized (this) {
            if (period != currentSamplingPeriod) {
                currentSamplingPeriod = period;
                samplesInCurrentPeriod = 1;
                return true;
            }
            if (samplesInCurrentPeriod >= maxSamplesPerPeriod) {
                return false;
            }
            ++samplesInCurrentPeriod;
            return true;
        }
    }

}
