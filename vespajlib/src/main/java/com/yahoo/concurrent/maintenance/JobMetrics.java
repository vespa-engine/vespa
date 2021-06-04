// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Tracks and forwards maintenance job metrics.
 *
 * @author mpolden
 */
public class JobMetrics {

    private final BiConsumer<String, Long> metricConsumer;

    private final ConcurrentHashMap<String, Long> incompleteRuns = new ConcurrentHashMap<>();

    public JobMetrics(BiConsumer<String, Long> metricConsumer) {
        this.metricConsumer = metricConsumer;
    }

    /** Record a run for given job */
    public void recordRunOf(String job) {
        incompleteRuns.merge(job, 1L, Long::sum);
    }

    /** Record completion of given job */
    public void recordCompletionOf(String job) {
        incompleteRuns.put(job, 0L);
    }

    /** Forward metrics for given job to metric consumer */
    public void forward(String job) {
        Long incompleteRuns = this.incompleteRuns.get(job);
        if (incompleteRuns != null) {
            metricConsumer.accept(job, incompleteRuns);
        }
    }

}
