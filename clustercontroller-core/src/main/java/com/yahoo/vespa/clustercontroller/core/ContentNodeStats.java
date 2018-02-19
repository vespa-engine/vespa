// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        private long bucketsTotal;
        private long bucketsPending;

        public BucketSpaceStats() {
            this.bucketsTotal = 0;
            this.bucketsPending = 0;
        }

        public BucketSpaceStats(long bucketsTotal, long bucketsPending) {
            this.bucketsTotal = bucketsTotal;
            this.bucketsPending = bucketsPending;
        }

        public long getBucketsTotal() {
            return bucketsTotal;
        }

        public long getBucketsPending() {
            return bucketsPending;
        }

        public void merge(BucketSpaceStats rhs, int factor) {
            this.bucketsTotal += (factor * rhs.bucketsTotal);
            this.bucketsPending += (factor * rhs.bucketsPending);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BucketSpaceStats that = (BucketSpaceStats) o;
            return bucketsTotal == that.bucketsTotal &&
                    bucketsPending == that.bucketsPending;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucketsTotal, bucketsPending);
        }

        @Override
        public String toString() {
            return "{bucketsTotal=" + bucketsTotal + ", bucketsPending=" + bucketsPending + "}";
        }
    }

    public ContentNodeStats(StorageNode storageNode) {
        this.nodeIndex = storageNode.getIndex();
        for (StorageNode.BucketSpaceStats stats : storageNode.getBucketSpacesStats()) {
            if (stats.valid()) {
                this.bucketSpaces.put(stats.getName(),
                        new BucketSpaceStats(stats.getBucketStats().getTotal(),
                                stats.getBucketStats().getPending()));
            } else {
                // TODO: better handling of invalid bucket space stats
                this.bucketSpaces.put(stats.getName(), new BucketSpaceStats());
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
                statsToUpdate = new BucketSpaceStats();
                bucketSpaces.put(entry.getKey(), statsToUpdate);
            }
            if (statsToUpdate != null) {
                statsToUpdate.merge(entry.getValue(), factor);
            }
        }
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
