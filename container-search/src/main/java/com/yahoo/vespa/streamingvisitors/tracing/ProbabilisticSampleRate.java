// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors.tracing;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Simple implementation of OpenCensus algorithm for probabilistic rate limiting as outlined in
 * https://github.com/census-instrumentation/opencensus-specs/blob/master/trace/Sampling.md
 */
public class ProbabilisticSampleRate implements SamplingStrategy {

    private final MonotonicNanoClock nanoClock;
    private final Supplier<Random> randomSupplier;
    private final double desiredSamplesPerSec;
    private final AtomicLong lastSampledAtNanoTime = new AtomicLong(0);

    public ProbabilisticSampleRate(MonotonicNanoClock nanoClock,
                                   Supplier<Random> randomSupplier,
                                   double desiredSamplesPerSec)
    {
        this.nanoClock = nanoClock;
        this.randomSupplier = randomSupplier;
        this.desiredSamplesPerSec = desiredSamplesPerSec;
    }

    public static ProbabilisticSampleRate withSystemDefaults(double desiredSamplesPerSec) {
        return new ProbabilisticSampleRate(System::nanoTime, ThreadLocalRandom::current, desiredSamplesPerSec);
    }

    @Override
    public boolean shouldSample() {
        // This load might race with the store below, causing multiple threads to get a sample
        // since the new timestamp has not been written yet, but it is extremely unlikely and
        // the consequences are not severe since this is a probabilistic sampler that does not
        // provide hard lower or upper bounds.
        long lastSampledAt = lastSampledAtNanoTime.get(); // TODO getPlain? No transitive visibility requirements
        long now = nanoClock.nanoTimeNow();
        double secsSinceLastSample = (now - lastSampledAt) / 1_000_000_000.0;
        // As the time elapsed since last sample increases, so does the probability of a new sample
        // being selected.
        double sampleProb = Math.min(secsSinceLastSample * desiredSamplesPerSec, 1.0);
        if (randomSupplier.get().nextDouble() < sampleProb) {
            lastSampledAtNanoTime.set(now); // TODO setPlain? No transitive visibility requirements
            return true;
        } else {
            return false;
        }
    }
}
