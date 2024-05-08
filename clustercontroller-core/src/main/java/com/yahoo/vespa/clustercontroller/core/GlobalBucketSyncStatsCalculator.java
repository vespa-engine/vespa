// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Optional;

/**
 * @author vekterli
 */
public class GlobalBucketSyncStatsCalculator {

    /**
     * Compute a value in [0, 1] representing how much of the cluster's data space is currently
     * out of sync, i.e. pending merging. In other words, if the value is 1 all buckets are out
     * of sync, and conversely if it's 0 all buckets are in sync. This number applies across bucket
     * spaces.
     *
     * @param globalStats Globally aggregated content node statistics for the entire cluster.
     * @return Optional containing a value [0, 1] representing the ratio of buckets pending merge
     *         in relation to the total number of buckets in the cluster, or an empty optional if
     *         the underlying global statistics contains invalid/incomplete information.
     */
    public static Optional<Double> clusterBucketsOutOfSyncRatio(ContentNodeStats globalStats) {
        long totalBuckets = 0;
        long pendingBuckets = 0;
        for (var space : globalStats.getBucketSpaces().values()) {
            if (!space.valid()) {
                return Optional.empty();
            }
            totalBuckets   += space.getBucketsTotal();
            pendingBuckets += space.getBucketsPending();
        }
        // It's currently possible for the reported number of pending buckets to be greater than
        // the number of total buckets. Example: this can happen if a bucket is present on a single
        // node, but should have been replicated to 9 more nodes. Since counts are not normalized
        // across content nodes for a given bucket, this will be counted as 9 pending and 1 total.
        // Eventually this will settle as 0 pending and 10 total.
        // TODO report node-normalized pending/total counts from distributors and use these.
        pendingBuckets = Math.min(pendingBuckets, totalBuckets);
        if (totalBuckets <= 0) {
            return Optional.of(0.0); // No buckets; cannot be out of sync by definition
        }
        return Optional.of((double)pendingBuckets / (double)totalBuckets);
    }

}
