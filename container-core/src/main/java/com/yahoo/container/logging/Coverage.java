// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

/**
 * Carry information about how the query covered the document corpus.
 */
public class Coverage {
    private final long docs;
    private final long active;
    private final long targetActive;
    private final int degradedReason;
    private final static int DEGRADED_BY_MATCH_PHASE = 1;
    private final static int DEGRADED_BY_TIMEOUT = 2;
    private final static int DEGRADED_BY_ADAPTIVE_TIMEOUT = 4;
    public Coverage(long docs, long active, long targetActive, int degradedReason) {
        this.docs = docs;
        this.active = active;
        this.targetActive = targetActive;
        this.degradedReason = degradedReason;
    }

    public long getDocs() {
        return docs;
    }

    public long getActive() {
        return active;
    }

    public static int toDegradation(boolean degradeByMatchPhase, boolean degradedByTimeout, boolean degradedByAdaptiveTimeout) {
        int v = 0;
        if (degradeByMatchPhase) {
            v |= DEGRADED_BY_MATCH_PHASE;
        }
        if (degradedByTimeout) {
            v |= DEGRADED_BY_TIMEOUT;
        }
        if (degradedByAdaptiveTimeout) {
            v |= DEGRADED_BY_ADAPTIVE_TIMEOUT;
        }
        return v;
    }

    public long getTargetActive() { return targetActive; }

    public boolean isDegraded() { return (degradedReason != 0) || isDegradedByNonIdealState(); }
    public boolean isDegradedByMatchPhase() { return (degradedReason & DEGRADED_BY_MATCH_PHASE) != 0; }
    public boolean isDegradedByTimeout() { return (degradedReason & DEGRADED_BY_TIMEOUT) != 0; }
    public boolean isDegradedByAdapativeTimeout() { return (degradedReason & DEGRADED_BY_ADAPTIVE_TIMEOUT) != 0; }
    public boolean isDegradedByNonIdealState() { return (degradedReason == 0) && (getResultPercentage() != 100);}

    /**
     * An int between 0 (inclusive) and 100 (inclusive) representing how many
     * percent coverage the result sets this Coverage instance contains information
     * about had.
     */
    public int getResultPercentage() {
        long total = targetActive;
        if (docs < total) {
            return (int) Math.round(docs * 100.0d / total);
        }
        return 100;
    }

}
