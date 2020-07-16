// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Tracks and forwards maintenance job metrics.
 *
 * @author mpolden
 */
public class JobMetrics {

    private final Clock clock;
    private final BiConsumer<String, Instant> metricConsumer;

    private final Map<String, Instant> successfulRuns = new ConcurrentHashMap<>();

    public JobMetrics(Clock clock, BiConsumer<String, Instant> metricConsumer) {
        this.clock = Objects.requireNonNull(clock);
        this.metricConsumer = metricConsumer;
    }

    /** Record successful run of given job */
    public void recordSuccessOf(String job) {
        successfulRuns.put(job, clock.instant());
    }

    /** Forward metrics for given job to metric consumer */
    public void forward(String job) {
        Instant lastSuccess = successfulRuns.get(job);
        if (lastSuccess != null) {
            metricConsumer.accept(job, lastSuccess);
        }
    }

}
