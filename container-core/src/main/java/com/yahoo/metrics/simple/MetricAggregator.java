// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.yahoo.concurrent.ThreadLocalDirectory;
import com.yahoo.metrics.ManagerConfig;

/**
 * Worker thread to collect the data stored in worker threads and build
 * snapshots for external consumption. Using the correct executor gives the
 * necessary guarantees for this being invoked from only a single thread.
 *
 * @author Steinar Knutsen
 */
class MetricAggregator implements Runnable {

    private final ThreadLocalDirectory<Bucket, Sample> metricsCollection;
    private final AtomicReference<Bucket> currentSnapshot;
    private int generation = 0;
    private final Bucket[] buffer;
    private long fromMillis;
    private final DimensionCache dimensions;

    MetricAggregator(ThreadLocalDirectory<Bucket, Sample> metricsCollection,
                     AtomicReference<Bucket> currentSnapshot,
                     ManagerConfig settings) {
        if (settings.reportPeriodSeconds() < 10) {
            throw new IllegalArgumentException("Do not use this metrics implementation" +
                                               " if report periods of less than 10 seconds is desired.");
        }
        buffer = new Bucket[settings.reportPeriodSeconds()];
        dimensions = new DimensionCache(settings.pointsToKeepPerMetric());
        fromMillis = System.currentTimeMillis();
        this.metricsCollection = metricsCollection;
        this.currentSnapshot = currentSnapshot;
    }

    @Override
    public void run() {
        Bucket toDelete = updateBuffer();
        createSnapshot(toDelete);
    }

    private void createSnapshot(Bucket toDelete) {
        Bucket toPresent = new Bucket();
        for (Bucket b : buffer) {
            if (b == null) {
                continue;
            }
            toPresent.merge(b);
        }
        dimensions.updateDimensionPersistence(toDelete, toPresent);
        currentSnapshot.set(toPresent);
    }

    private Bucket updateBuffer() {
        List<Bucket> buckets = metricsCollection.fetch();
        long toMillis = System.currentTimeMillis();
        int bucketIndex = generation++ % buffer.length;
        Bucket bucketToDelete = buffer[bucketIndex];
        Bucket latest = new Bucket(fromMillis, toMillis);
        for (Bucket b : buckets) {
            latest.merge(b, true);
        }
        buffer[bucketIndex] = latest;
        this.fromMillis = toMillis;
        return bucketToDelete;
    }

}
