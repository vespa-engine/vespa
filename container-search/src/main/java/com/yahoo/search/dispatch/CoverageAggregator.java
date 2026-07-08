// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.DocumentCount;
import com.yahoo.search.result.Coverage;

import static com.yahoo.container.handler.Coverage.DEGRADED_BY_ADAPTIVE_TIMEOUT;

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
    private int degradedReason = 0;

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
        degradedReason = rhs.degradedReason;
    }
    void add(Coverage source) {
        answeredDocs += source.getDocs();
        answeredActiveDocs += source.getActive();
        answeredTargetActiveDocs += source.getTargetActive();
        answeredNodesParticipated += source.getNodes();
        answeredNodes++;
        degradedReason |= source.getDegradedReason();
    }

    public int getAskedNodes() {
        return askedNodes;
    }
    public int getAnsweredNodes() {
        return answeredNodes;
    }
    public boolean hasNoAnswers() { return answeredNodes == 0; }

    public void setFailedNodes(int failedNodes) {
        this.failedNodes = failedNodes;
    }

    public Coverage createCoverage(TimeoutHandler timeoutHandler) {
        Coverage coverage = new Coverage(answeredDocs, answeredActiveDocs, answeredNodesParticipated, 1);
        coverage.setNodesTried(askedNodes);
        coverage.setTargetActive(answeredTargetActiveDocs);
        coverage.setDegradedReason(degradedReason | timeoutHandler.reason());
        return coverage;
    }

    public CoverageAggregator adjustedDegradedCoverage(int redundancy, TimeoutHandler timeoutHandler, DocumentCount documentCount) {
        int askedAndFailed = askedNodes + failedNodes;
        if (askedAndFailed == answeredNodesParticipated) {
            return this;
        }
        int notAnswered = askedAndFailed - answeredNodesParticipated;

        if (timeoutHandler.reason() == DEGRADED_BY_ADAPTIVE_TIMEOUT) {
            CoverageAggregator clone = new CoverageAggregator(this);
            return clone.adjustActiveDocs(notAnswered, documentCount);
        } else {
            if (askedAndFailed > answeredNodesParticipated) {
                CoverageAggregator clone = new CoverageAggregator(this);
                int missingNodes = notAnswered - (redundancy - 1);
                if (missingNodes > 0) {
                    clone.adjustActiveDocs(missingNodes, documentCount);
                }
                clone.degradedReason |= timeoutHandler.reason();
                return clone;
            }
        }
        return this;
    }

    private CoverageAggregator adjustActiveDocs(int numMissingNodes, DocumentCount documentCount) {
        if (answeredNodesParticipated > 0) {
            answeredActiveDocs += (numMissingNodes * answeredActiveDocs / answeredNodesParticipated);
            answeredTargetActiveDocs += (numMissingNodes * answeredTargetActiveDocs / answeredNodesParticipated);

            // Check that the document count is reliable (there was a group with all nodes online)
            // and that it is not the initial value of 0.
            if (documentCount.isReliable() && documentCount.getActiveDocuments() > 0 && documentCount.getTargetActiveDocuments() > 0) {
                answeredActiveDocs = Math.min(answeredActiveDocs, documentCount.getActiveDocuments());
                answeredTargetActiveDocs = Math.min(answeredTargetActiveDocs, documentCount.getTargetActiveDocuments());
            }
        }
        return this;
    }
}
