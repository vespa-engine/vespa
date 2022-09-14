package com.yahoo.search.dispatch;

import com.yahoo.concurrent.Timer;
import com.yahoo.search.Query;
import com.yahoo.vespa.config.search.DispatchConfig;

import static com.yahoo.container.handler.Coverage.DEGRADED_BY_ADAPTIVE_TIMEOUT;

/**
 * Computes next timeout based on how many responses has been received so far
 *
 * @author baldersheim
 */
class AdaptiveTimeoutHandler implements TimeoutHandler {
    private final double minimumCoverage;
    private final int askedNodes;
    private final int minimumResponses;
    private final Query query;
    private final Timer timer;
    private final DispatchConfig config;

    private long deadline;
    private long adaptiveTimeoutMin;
    private long adaptiveTimeoutMax;
    boolean adaptiveTimeoutCalculated = false;

    AdaptiveTimeoutHandler(Timer timer, DispatchConfig config, int askedNodes, Query query) {
        minimumCoverage = config.minSearchCoverage();
        this.config = config;
        this.askedNodes = askedNodes;
        this.query = query;
        this.timer = timer;
        minimumResponses = (int) Math.ceil(askedNodes * minimumCoverage / 100.0);
        deadline = timer.milliTime() + query.getTimeLeft();
    }

    @Override
    public long nextTimeoutMS(int answeredNodes) {
        if (askedNodes == answeredNodes) return query.getTimeLeft();  // All nodes have responded - done
        if (answeredNodes < minimumResponses) return query.getTimeLeft(); // Minimum responses have not been received yet

        if (!adaptiveTimeoutCalculated) {
            // Recompute timeout when minimum responses have been received
            long timeLeftMs = query.getTimeLeft();
            adaptiveTimeoutMin = (long) (timeLeftMs * config.minWaitAfterCoverageFactor());
            adaptiveTimeoutMax = (long) (timeLeftMs * config.maxWaitAfterCoverageFactor());
            adaptiveTimeoutCalculated = true;
        }
        long now = timer.milliTime();
        int pendingQueries = askedNodes - answeredNodes;
        double missWidth = ((100.0 - minimumCoverage) * askedNodes) / 100.0 - 1.0;
        double slopedWait = adaptiveTimeoutMin;
        if (pendingQueries > 1 && missWidth > 0.0) {
            slopedWait += ((adaptiveTimeoutMax - adaptiveTimeoutMin) * (pendingQueries - 1)) / missWidth;
        }
        long nextAdaptive = (long) slopedWait;
        if (now + nextAdaptive >= deadline) {
            return deadline - now;
        }
        deadline = now + nextAdaptive;

        return nextAdaptive;
    }

    @Override
    public int reason() {
        return DEGRADED_BY_ADAPTIVE_TIMEOUT;
    }
}
