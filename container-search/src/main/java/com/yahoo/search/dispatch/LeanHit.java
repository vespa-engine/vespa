package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacketData;

import java.util.Arrays;

public class LeanHit implements Comparable<LeanHit> {
    private final byte [] gid;
    private final double relevance;
    private final byte [] sortData;
    private final int partId;
    private final int distributionKey;
    //TODO Remove when FS4 is gone
    private QueryPacketData queryPacketData;
    public LeanHit(byte [] gid, int partId, int distributionKey, double relevance) {
        this.gid = gid;
        this.relevance = relevance;
        this.sortData = null;
        this.partId = partId;
        this.distributionKey = distributionKey;
    }
    public LeanHit(byte [] gid, int partId, int distributionKey, byte [] sortData) {
        this.gid = gid;
        this.relevance = Double.NEGATIVE_INFINITY;
        this.sortData = sortData;
        this.partId = partId;
        this.distributionKey = distributionKey;
    }
    double getRelevance() { return relevance; }
    byte [] getGid() { return gid; }
    byte [] getSortData() { return sortData; }
    boolean hasSortData() { return sortData != null; }
    int getPartId() { return partId; }
    int getDistributionKey() { return distributionKey; }

    QueryPacketData getQueryPacketData() { return queryPacketData; }

    public void setQueryPacketData(QueryPacketData queryPacketData) {
        this.queryPacketData = queryPacketData;
    }

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
