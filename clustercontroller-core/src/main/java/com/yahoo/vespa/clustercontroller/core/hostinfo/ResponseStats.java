// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public record ResponseStats(double sampleWindowSec,
                            long totalResponseCount, // Total number of responses received in total, errors + OK
                            long networkErrorCount,
                            long clockSkewErrorCount,
                            long uncategorizedErrorCount) {

    public record Errors(@JsonProperty("network") Long network,
                         @JsonProperty("clock-skew") Long clockSkew,
                         @JsonProperty("uncategorized") Long uncategorized) {
    }

    @JsonCreator
    public ResponseStats(@JsonProperty("sample-window-sec") Double sampleWindowSec,
                         @JsonProperty("total-count") Long totalResponseCount,
                         @JsonProperty("errors") Errors errors) {
        this(Math.max(sampleWindowSec != null ? sampleWindowSec : 60.0, 1.0),
             totalResponseCount != null ? totalResponseCount : 0,
             errors != null ? valueOrZero(errors.network) : 0,
             errors != null ? valueOrZero(errors.clockSkew) : 0,
             errors != null ? valueOrZero(errors.uncategorized) : 0);
    }

    private static long valueOrZero(Long valOrNull) {
        return valOrNull != null ? valOrNull : 0;
    }

    public static ResponseStats makeEmpty() {
        return new ResponseStats(60.0, 0, 0, 0, 0);
    }

    public boolean hasNetworkErrors() { return networkErrorCount > 0; }
    public boolean hasClockSkewErrors() { return clockSkewErrorCount > 0; }
    public boolean hasUncategorizedErrors() { return uncategorizedErrorCount > 0; }

    public boolean hasErrors() {
        return hasNetworkErrors() || hasClockSkewErrors() || hasUncategorizedErrors();
    }

}
