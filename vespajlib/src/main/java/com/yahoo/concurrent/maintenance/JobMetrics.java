// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent.maintenance;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and forwards maintenance job metrics.
 *
 * @author mpolden
 */
public abstract class JobMetrics {

    /**
     * Records completion of a run of a job.
     * This is guaranteed to always be called once after each maintainer run.
     */
    public abstract void completed(String job, double successFactor, long durationMs);

}
