// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * TODO doc
 *
 * @author vekterli
 */
public class ResponseStats {

    private final double sampleWindowSec; // TODO remove?
    private final long totalResponseCount; // Total number of responses received in total, errors + OK
    private final long networkErrorCount;
    private final long clockSkewErrorCount;
    private final long otherErrorCount;

    public ResponseStats(@JsonProperty("sample-window-sec") Double sampleWindowSec,
                         @JsonProperty("total-count") Long totalResponseCount,
                         @JsonProperty("network-errors") Long networkErrorCount,
                         @JsonProperty("clock-skew-errors") Long clockSkewErrorCount,
                         @JsonProperty("other-errors") Long otherErrorCount) {
        this.sampleWindowSec     = valueOrDefault(sampleWindowSec, 60.0);
        this.totalResponseCount  = valueOrDefault(totalResponseCount, 0);
        this.networkErrorCount   = valueOrDefault(networkErrorCount, 0);
        this.clockSkewErrorCount = valueOrDefault(clockSkewErrorCount, 0);
        this.otherErrorCount     = valueOrDefault(otherErrorCount, 0);
    }

    public static ResponseStats makeEmpty() {
        return new ResponseStats(0.0, 0L, 0L, 0L, 0L);
    }

    private static long valueOrDefault(Long valOrNull, long defaultValue) {
        return valOrNull != null ? valOrNull : defaultValue;
    }

    private static double valueOrDefault(Double valOrNull, double defaultValue) {
        return valOrNull != null ? valOrNull : defaultValue;
    }

    public long totalResponseCount() { return this.totalResponseCount; }
    public long networkErrorCount() { return this.networkErrorCount; }
    public long clockSkewErrorCount() { return this.clockSkewErrorCount; }
    public long otherErrorCount() { return this.otherErrorCount; }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResponseStats that = (ResponseStats) o;
        return Double.compare(sampleWindowSec, that.sampleWindowSec) == 0 &&
                totalResponseCount == that.totalResponseCount &&
                networkErrorCount == that.networkErrorCount &&
                clockSkewErrorCount == that.clockSkewErrorCount &&
                otherErrorCount == that.otherErrorCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleWindowSec, totalResponseCount, networkErrorCount,
                            clockSkewErrorCount, otherErrorCount);
    }

    public boolean hasErrors() {
        return this.networkErrorCount > 0 || this.clockSkewErrorCount > 0 || this.otherErrorCount > 0;
    }

    public boolean hasNetworkErrors() {
        return this.networkErrorCount > 0;
    }

}
