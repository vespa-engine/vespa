package com.yahoo.search.dispatch;

import com.yahoo.search.result.Coverage;

import static com.yahoo.container.handler.Coverage.*;

/**
 * Aggregates coverage as responses are added.
 *
 * @author baldersheim
 */
public class CoverageAggregator {
    private final int askedNodes;
    private int answeredNodes = 0;
    private int answeredNodesParticipated = 0;
    private int failedNodes = 0;
    private long answeredDocs = 0;
    private long answeredActiveDocs = 0;
    private long answeredTargetActiveDocs = 0;
    private boolean timedOut = false;
    private boolean degradedByMatchPhase = false;
    CoverageAggregator(int askedNodes) {
        this.askedNodes = askedNodes;
    }
    CoverageAggregator(CoverageAggregator rhs) {
        askedNodes = rhs.askedNodes;
        answeredNodes = rhs.answeredNodes;
        answeredNodesParticipated = rhs.answeredNodesParticipated;
        failedNodes = rhs.failedNodes;
        answeredDocs = rhs.answeredDocs;
        answeredActiveDocs = rhs.answeredActiveDocs;
        answeredTargetActiveDocs = rhs.answeredTargetActiveDocs;
        timedOut = rhs.timedOut;
        degradedByMatchPhase = rhs.degradedByMatchPhase;
    }
    void add(Coverage source) {
        answeredDocs += source.getDocs();
        answeredActiveDocs += source.getActive();
        answeredTargetActiveDocs += source.getTargetActive();
        answeredNodesParticipated += source.getNodes();
        answeredNodes++;
        degradedByMatchPhase |= source.isDegradedByMatchPhase();
        timedOut |= source.isDegradedByTimeout();
    }
    public int getAskedNodes() {
        return askedNodes;
    }
    public int getAnsweredNodes() {
        return answeredNodes;
    }
    public boolean hasNoAnswers() { return answeredNodes == 0; }
    public void setTimedOut() { timedOut = true; }
    public void setFailedNodes(int failedNodes) {
        this.failedNodes = failedNodes;
    }

    public Coverage createCoverage(TimeoutHandler timeoutHandler) {
        Coverage coverage = new Coverage(answeredDocs, answeredActiveDocs, answeredNodesParticipated, 1);
        coverage.setNodesTried(askedNodes);
        coverage.setTargetActive(answeredTargetActiveDocs);
        int degradedReason = 0;
        if (timedOut) {
            degradedReason |= timeoutHandler.reason();
        }
        if (degradedByMatchPhase) {
            degradedReason |= DEGRADED_BY_MATCH_PHASE;
        }
        coverage.setDegradedReason(degradedReason);
        return coverage;
    }
    public CoverageAggregator adjustedDegradedCoverage(int searchableCopies, TimeoutHandler timeoutHandler) {
        int askedAndFailed = askedNodes + failedNodes;
        if (askedAndFailed == answeredNodesParticipated) {
            return this;
        }
        int notAnswered = askedAndFailed - answeredNodesParticipated;

        if ((timeoutHandler.reason() == DEGRADED_BY_ADAPTIVE_TIMEOUT) && answeredNodesParticipated > 0) {
            CoverageAggregator clone = new CoverageAggregator(this);
            clone.answeredActiveDocs += (notAnswered * answeredActiveDocs / answeredNodesParticipated);
            clone.answeredTargetActiveDocs += (notAnswered * answeredTargetActiveDocs / answeredNodesParticipated);
            return clone;
        } else {
            if (askedAndFailed > answeredNodesParticipated) {
                int missingNodes = notAnswered - (searchableCopies - 1);
                if (answeredNodesParticipated > 0) {
                    CoverageAggregator clone = new CoverageAggregator(this);
                    clone.answeredActiveDocs += (missingNodes * answeredActiveDocs / answeredNodesParticipated);
                    clone.answeredTargetActiveDocs += (missingNodes * answeredTargetActiveDocs / answeredNodesParticipated);
                    clone.timedOut = true;
                    return clone;
                }
            }
        }
        return this;
    }
}
