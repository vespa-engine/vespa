// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.data.access.Inspector;
import com.yahoo.search.result.FeatureData;

import java.util.Arrays;

/**
 * @author baldersheim
 */
public class LeanHit implements Comparable<LeanHit> {

    private final byte[] gid;
    private final double relevance;
    private final byte[] sortData;
    private final int partId;
    private final int distributionKey;
    private FeatureData matchFeatures;

    public LeanHit(byte[] gid, int partId, int distributionKey, double relevance) {
        this(gid, partId, distributionKey, relevance, null);
    }
    public LeanHit(byte[] gid, int partId, int distributionKey, double relevance, byte[] sortData) {
        this.gid = gid;
        this.relevance = Double.isNaN(relevance) ? Double.NEGATIVE_INFINITY : relevance;
        this.sortData = sortData;
        this.partId = partId;
        this.distributionKey = distributionKey;
        this.matchFeatures = null;
    }

    public double getRelevance() { return relevance; }
    public byte[] getGid() { return gid; }
    public byte[] getSortData() { return sortData; }
    public boolean hasSortData() { return sortData != null; }
    public int getPartId() { return partId; }
    public int getDistributionKey() { return distributionKey; }

    public FeatureData getMatchFeatures() { return matchFeatures; }
    public boolean hasMatchFeatures() { return matchFeatures != null; }
    public void addMatchFeatures(Inspector features) {
        matchFeatures = new FeatureData(features);
    }

    @Override
    public int compareTo(LeanHit o) {
        int res = (sortData != null)
                ? compareData(sortData, o.sortData)
                : Double.compare(o.relevance, relevance);
        return (res != 0) ? res : compareData(gid, o.gid);
    }

    public static int compareData(byte[] left, byte[] right) {
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
