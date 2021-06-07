// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and forwards maintenance job metrics.
 *
 * @author mpolden
 */
public abstract class JobMetrics {

    private final ConcurrentHashMap<String, Long> incompleteRuns = new ConcurrentHashMap<>();

    /** Record starting of a run of a job */
    public void starting(String job) {
        incompleteRuns.merge(job, 1L, Long::sum);
    }

    /** Record completion of given job */
    public void recordCompletionOf(String job) {
        incompleteRuns.put(job, 0L);
    }

    /**
     * Records completion of a run of a job.
     * This is guaranteed to always be called once whenever starting has been called.
     */
    public void completed(String job, double successFactor) {
        Long incompleteRuns = this.incompleteRuns.get(job);
        if (incompleteRuns != null) {
            recordCompletion(job, incompleteRuns, successFactor);
        }
    }

    protected abstract void recordCompletion(String job, Long incompleteRuns, double successFactor);

}
