// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNode;

import java.util.*;

/**
 * @author hakonhall
 */
public class ContentNodeStats {

    private int nodeIndex;
    private Map<String, BucketSpaceStats> bucketSpaces = new HashMap<>();

    public static class BucketSpaceStats {
        private int invalidCount;
        private long bucketsTotal;
        private long bucketsPending;

        private BucketSpaceStats() {
            this.invalidCount = 1;
            this.bucketsTotal = 0;
            this.bucketsPending = 0;
        }

        private BucketSpaceStats(long bucketsTotal, long bucketsPending, boolean invalid) {
            this.invalidCount = (invalid ? 1 : 0);
            this.bucketsTotal = bucketsTotal;
            this.bucketsPending = bucketsPending;
        }

        public static BucketSpaceStats invalid() {
            return new BucketSpaceStats();
        }

        public static BucketSpaceStats invalid(long bucketsTotal, long bucketsPending) {
            return new BucketSpaceStats(bucketsTotal, bucketsPending, true);
        }

        public static BucketSpaceStats of(long bucketsTotal, long bucketsPending) {
            return new BucketSpaceStats(bucketsTotal, bucketsPending, false);
        }

        public static BucketSpaceStats empty() {
            return new BucketSpaceStats(0, 0, false);
        }

        public long getBucketsTotal() {
            return bucketsTotal;
        }

        public long getBucketsPending() {
            return bucketsPending;
        }

        public boolean mayHaveBucketsPending() {
            return (bucketsPending > 0) || (invalidCount > 0);
        }

        public boolean valid() {
            return invalidCount == 0;
        }

        public void merge(BucketSpaceStats rhs, int factor) {
            this.invalidCount += (factor * rhs.invalidCount);
            this.bucketsTotal += (factor * rhs.bucketsTotal);
            this.bucketsPending += (factor * rhs.bucketsPending);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BucketSpaceStats that = (BucketSpaceStats) o;
            return invalidCount == that.invalidCount &&
                    bucketsTotal == that.bucketsTotal &&
                    bucketsPending == that.bucketsPending;
        }

        @Override
        public int hashCode() {
            return Objects.hash(invalidCount, bucketsTotal, bucketsPending);
        }

        @Override
        public String toString() {
            return "{bucketsTotal=" + bucketsTotal + ", bucketsPending=" + bucketsPending + ", invalidCount=" + invalidCount + "}";
        }
    }

    public ContentNodeStats(StorageNode storageNode) {
        this.nodeIndex = storageNode.getIndex();
        for (StorageNode.BucketSpaceStats stats : storageNode.getBucketSpacesStats()) {
            if (stats.valid()) {
                this.bucketSpaces.put(stats.getName(),
                        BucketSpaceStats.of(stats.getBucketStats().getTotal(),
                                stats.getBucketStats().getPending()));
            } else {
                this.bucketSpaces.put(stats.getName(), BucketSpaceStats.invalid());
            }
        }
    }

    public ContentNodeStats(int index) {
        this(index, new HashMap<>());
    }

    public ContentNodeStats(int index, Map<String, BucketSpaceStats> bucketSpaces) {
        this.nodeIndex = index;
        this.bucketSpaces = bucketSpaces;
    }

    public int getNodeIndex() { return nodeIndex; }

    public void add(ContentNodeStats stats) {
        merge(stats, 1);
    }

    public void subtract(ContentNodeStats stats) {
        merge(stats, -1);
    }

    private void merge(ContentNodeStats stats, int factor) {
        for (Map.Entry<String, BucketSpaceStats> entry : stats.bucketSpaces.entrySet()) {
            BucketSpaceStats statsToUpdate = bucketSpaces.get(entry.getKey());
            if (statsToUpdate == null && factor == 1) {
                statsToUpdate = BucketSpaceStats.empty();
                bucketSpaces.put(entry.getKey(), statsToUpdate);
            }
            if (statsToUpdate != null) {
                statsToUpdate.merge(entry.getValue(), factor);
            }
        }
    }

    public BucketSpaceStats getBucketSpace(String bucketSpace) {
        return bucketSpaces.get(bucketSpace);
    }

    public Map<String, BucketSpaceStats> getBucketSpaces() {
        return bucketSpaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentNodeStats that = (ContentNodeStats) o;
        return nodeIndex == that.nodeIndex &&
                Objects.equals(bucketSpaces, that.bucketSpaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeIndex, bucketSpaces);
    }

    @Override
    public String toString() {
        return String.format("{index=%d, bucketSpaces=[%s]}",
                nodeIndex, Arrays.toString(bucketSpaces.entrySet().toArray()));
    }
}
