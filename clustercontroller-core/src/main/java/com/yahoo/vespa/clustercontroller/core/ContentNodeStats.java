// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.hostinfo.StorageNode;

/**
 * @author hakonhall
 */
public class ContentNodeStats {

    /**
     * Constructor that sets values to zero if not present.
     */
    public ContentNodeStats(StorageNode storageNodePojo) {
        this.nodeIndex = storageNodePojo.getIndex();

        StorageNode.OutstandingMergeOps mergeOps = storageNodePojo.getOutstandingMergeOpsOrNull();
        if (mergeOps == null) {
            mergeOps = new StorageNode.OutstandingMergeOps();
        }
        syncing = createAmount(mergeOps.getSyncingOrNull());
        copyingIn = createAmount(mergeOps.getCopyingInOrNull());
        movingOut = createAmount(mergeOps.getMovingOutOrNull());
        copyingOut = createAmount(mergeOps.getCopyingOutOrNull());
    }

    private static Amount createAmount(StorageNode.Buckets bucketOrNull) {
        if (bucketOrNull == null) {
            return new Amount();
        }
        return new Amount(bucketOrNull.getBuckets());
    }

    static public class Amount {
        private long buckets;

        Amount() { this(0); }
        Amount(long buckets) { this.buckets = buckets; }

        public void set(Amount other) {
            buckets = other.buckets;
        }

        public long getBuckets() {
            return buckets;
        }

        /**
         * Logically, add (factor * amount) to this object.
         */
        void scaledAdd(int factor, Amount amount) {
            buckets += factor * amount.buckets;
        }

        public boolean equals(Object other) {
            if (!(other instanceof Amount)) {
                return false;
            }
            Amount otherAmount = (Amount) other;
            return buckets == otherAmount.buckets;
        }

        public int hashCode() {
                return (int)buckets;
        }

        public String toString() {
            return String.format("{buckets = %d}", buckets);
        }
    }

    private final Amount syncing;
    private final Amount copyingIn;
    private final Amount movingOut;
    private final Amount copyingOut;
    private int nodeIndex;

    /**
     * An instance with all 0 amounts.
     */
    public ContentNodeStats(int index) {
        this(index, new Amount(), new Amount(), new Amount(), new Amount());
    }

    ContentNodeStats(int index, Amount syncing, Amount copyingIn, Amount movingOut, Amount copyingOut) {
        this.nodeIndex = index;
        this.syncing = syncing;
        this.copyingIn = copyingIn;
        this.movingOut = movingOut;
        this.copyingOut = copyingOut;
    }

    public void set(ContentNodeStats stats) {
        nodeIndex = stats.nodeIndex;
        syncing.set(stats.syncing);
        copyingIn.set(stats.copyingIn);
        movingOut.set(stats.movingOut);
        copyingOut.set(stats.copyingOut);
    }

    int getNodeIndex() { return nodeIndex; }
    public Amount getSyncing() { return syncing; }
    public Amount getCopyingIn() { return copyingIn; }
    public Amount getMovingOut() { return movingOut; }
    public Amount getCopyingOut() { return copyingOut; }

    void add(ContentNodeStats stats) {
        scaledAdd(1, stats);
    }

    void subtract(ContentNodeStats stats) {
        scaledAdd(-1, stats);
    }

    /**
     * Logically, adds (factor * stats) to this object. factor of 1 is normal add, -1 is subtraction.
     */
    private void scaledAdd(int factor, ContentNodeStats stats) {
        syncing.scaledAdd(factor, stats.syncing);
        copyingIn.scaledAdd(factor, stats.copyingIn);
        movingOut.scaledAdd(factor, stats.movingOut);
        copyingOut.scaledAdd(factor, stats.copyingOut);
    }

    @Override
    public int hashCode() {
        return (int) (syncing.buckets +
                copyingIn.buckets * 31 +
                movingOut.buckets * 17 +
                copyingOut.buckets * 7);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ContentNodeStats)) {
            return false;
        }

        ContentNodeStats otherStats = (ContentNodeStats) other;
        return nodeIndex == otherStats.nodeIndex &&
                syncing.equals(otherStats.syncing) &&
                copyingIn.equals(otherStats.copyingIn) &&
                movingOut.equals(otherStats.movingOut) &&
                copyingOut.equals(otherStats.copyingOut);
    }

    public String toString() {
        return String.format("{index = %d, syncing = %s, copyingIn = %s, movingOut = %s, copyingOut = %s}",
                nodeIndex, syncing, copyingIn, movingOut, copyingOut);
    }
}
