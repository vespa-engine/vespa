// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;


import java.util.Arrays;

public class LeanHit implements Comparable<LeanHit> {
    private final byte [] gid;
    private final double relevance;
    private final byte [] sortData;
    private final int partId;
    private final int distributionKey;

    public LeanHit(byte [] gid, int partId, int distributionKey, double relevance) {
        this.gid = gid;
        this.relevance = Double.isNaN(relevance) ? Double.NEGATIVE_INFINITY : relevance;
        this.sortData = null;
        this.partId = partId;
        this.distributionKey = distributionKey;
    }
    public LeanHit(byte [] gid, int partId, int distributionKey, byte [] sortData) {
        this.gid = gid;
        this.relevance = 0.0;
        this.sortData = sortData;
        this.partId = partId;
        this.distributionKey = distributionKey;
    }
    public double getRelevance() { return relevance; }
    public byte [] getGid() { return gid; }
    public byte [] getSortData() { return sortData; }
    public boolean hasSortData() { return sortData != null; }
    public int getPartId() { return partId; }
    public int getDistributionKey() { return distributionKey; }

    @Override
    public int compareTo(LeanHit o) {
        int res = (sortData != null)
                ? compareData(sortData, o.sortData)
                : Double.compare(o.relevance, relevance);
        return (res != 0) ? res : compareData(gid, o.gid);
    }

    private static int compareData(byte [] left, byte [] right) {
        int i = Arrays.mismatch(left, right);
        if (i < 0) {
            return 0;
        }
        int max = Integer.min(left.length, right.length);
        if (i >= max) {
            return left.length - right.length;
        }
        int vl = (int) left[i] & 0xFF;
        int vr = (int) right[i] & 0xFF;
        return vl - vr;
    }
}
