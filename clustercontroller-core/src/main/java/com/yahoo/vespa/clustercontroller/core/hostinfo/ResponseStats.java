// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Encapsulates response-related information received by _one_ particular distributor
 * related to its communication with _one_ particular content node. This is a superset
 * of information than what strictly covers error statistics (by including the total
 * number of responses, which includes successes), hence the name ResponseStats rather
 * than just "ErrorStats" or similar. Error-specific statistics may be further extracted
 * from this superset.
 *
 * @author vekterli
 */
public class ResponseStats {

    private final double sampleWindowSec;
    private final long totalResponseCount; // Total number of responses received in total, errors + OK
    private final long networkErrorCount;
    private final long clockSkewErrorCount;
    private final long uncategorizedErrorCount;

    public record Errors(@JsonProperty("network") Long network,
                         @JsonProperty("clock-skew") Long clockSkew,
                         @JsonProperty("uncategorized") Long uncategorized) {
    }

    public ResponseStats(@JsonProperty("sample-window-sec") Double sampleWindowSec,
                         @JsonProperty("total-count") Long totalResponseCount,
                         @JsonProperty("errors") Errors errors) {
        this.sampleWindowSec    = Math.max(valueOrDefault(sampleWindowSec, 60.0), 1.0);
        this.totalResponseCount = valueOrDefault(totalResponseCount, 0);
        if (errors != null) {
            this.networkErrorCount       = valueOrDefault(errors.network, 0);
            this.clockSkewErrorCount     = valueOrDefault(errors.clockSkew, 0);
            this.uncategorizedErrorCount = valueOrDefault(errors.uncategorized, 0);
        } else {
            this.networkErrorCount       = 0;
            this.clockSkewErrorCount     = 0;
            this.uncategorizedErrorCount = 0;
        }
    }

    public static ResponseStats makeEmpty() {
        return new ResponseStats(0.0, 0L, null);
    }

    private static long valueOrDefault(Long valOrNull, long defaultValue) {
        return valOrNull != null ? valOrNull : defaultValue;
    }

    private static double valueOrDefault(Double valOrNull, double defaultValue) {
        return valOrNull != null ? valOrNull : defaultValue;
    }

    public boolean hasNetworkErrors() { return networkErrorCount > 0; }
    public boolean hasClockSkewErrors() { return clockSkewErrorCount > 0; }
    public boolean hasUncategorizedErrors() { return uncategorizedErrorCount > 0; }

    public boolean hasErrors() {
        return hasNetworkErrors() || hasClockSkewErrors() || hasUncategorizedErrors();
    }

    public double sampleWindowSec() { return sampleWindowSec; }

    public long totalResponseCount() { return totalResponseCount; }
    public long networkErrorCount() { return networkErrorCount; }
    public long clockSkewErrorCount() { return clockSkewErrorCount; }
    public long uncategorizedErrorCount() { return uncategorizedErrorCount; }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResponseStats that = (ResponseStats) o;
        return Double.compare(sampleWindowSec, that.sampleWindowSec) == 0 &&
                totalResponseCount == that.totalResponseCount &&
                networkErrorCount == that.networkErrorCount &&
                clockSkewErrorCount == that.clockSkewErrorCount &&
                uncategorizedErrorCount == that.uncategorizedErrorCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleWindowSec, totalResponseCount, networkErrorCount,
                            clockSkewErrorCount, uncategorizedErrorCount);
    }

}
