// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;


import com.google.common.annotations.Beta;

/**
 * The coverage report for a result set.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 */
public class Coverage {

    protected long docs;
    protected long active;
    protected long soonActive;
    protected int degradedReason;
    protected int nodes;
    protected int resultSets;
    protected int fullResultSets;

    // need a default setting for deserialization logic in subclasses
    protected FullCoverageDefinition fullReason = FullCoverageDefinition.DOCUMENT_COUNT;

    protected enum FullCoverageDefinition {
        EXPLICITLY_FULL, EXPLICITLY_INCOMPLETE, DOCUMENT_COUNT;
    }

    private final static int DEGRADED_BY_MATCH_PHASE = 1;
    private final static int DEGRADED_BY_TIMEOUT = 2;
    private final static int DEGRADED_BY_ADAPTIVE_TIMEOUT = 4;

    /**
     * Build an invalid instance to initiate manually.
     */
    protected Coverage() {
    }

    protected Coverage(long docs, long active, int nodes, int resultSets) {
        this(docs, active, nodes, resultSets, FullCoverageDefinition.DOCUMENT_COUNT);
    }

    public Coverage(long docs, int nodes, boolean full) {
        this(docs, nodes, full, 1);
    }

    protected Coverage(long docs, int nodes, boolean full, int resultSets) {
        this(docs, docs, nodes, resultSets, full ? FullCoverageDefinition.EXPLICITLY_FULL
                : FullCoverageDefinition.EXPLICITLY_INCOMPLETE);
    }

    private Coverage(long docs, long active, int nodes, int resultSets, FullCoverageDefinition fullReason) {
        this.docs = docs;
        this.nodes = nodes;
        this.active = active;
        this.soonActive = active;
        this.degradedReason = 0;
        this.resultSets = resultSets;
        this.fullReason = fullReason;
        this.fullResultSets = getFull() ? resultSets : 0;
    }

    public void merge(Coverage other) {
        if (other == null) {
            return;
        }
        docs += other.getDocs();
        nodes += other.getNodes();
        active += other.getActive();
        soonActive += other.getSoonActive();
        degradedReason |= other.degradedReason;
        resultSets += other.getResultSets();
        fullResultSets += other.getFullResultSets();

        // explicitly incomplete beats doc count beats explicitly full
        switch (other.fullReason) {
        case EXPLICITLY_FULL:
            // do nothing
            break;
        case EXPLICITLY_INCOMPLETE:
            fullReason = FullCoverageDefinition.EXPLICITLY_INCOMPLETE;
            break;
        case DOCUMENT_COUNT:
            if (fullReason == FullCoverageDefinition.EXPLICITLY_FULL) {
                fullReason = FullCoverageDefinition.DOCUMENT_COUNT;
            }
            break;
        }
    }

    /**
     * The number of documents searched for this result. If the final result
     * set is produced through several queries, this number will be the sum
     * for all the queries.
     */
    public long getDocs() {
        return docs;
    }

    /**
     * Total number of documents that could be searched.
     *
     * @return Total number of active documents
     */
    public long getActive() { return active; }

    /**
     * Total number of documents that will be searchable once redistribution has settled.
     * Still in beta, sematics not finalized yet.
     *
     * @return Total number of documents that will soon be available.
     */
    @Beta
    public long getSoonActive() { return soonActive; }

    public boolean isDegraded() { return (degradedReason != 0) || isDegradedByNonIdealState(); }
    public boolean isDegradedByMatchPhase() { return (degradedReason & DEGRADED_BY_MATCH_PHASE) != 0; }
    public boolean isDegradedByTimeout() { return (degradedReason & DEGRADED_BY_TIMEOUT) != 0; }
    public boolean isDegradedByAdapativeTimeout() { return (degradedReason & DEGRADED_BY_ADAPTIVE_TIMEOUT) != 0; }
    public boolean isDegradedByNonIdealState() { return (degradedReason == 0) && (getResultPercentage() != 100);}

    /**
     * @return whether the search had full coverage or not
     */
    public boolean getFull() {
        switch (fullReason) {
        case EXPLICITLY_FULL:
            return true;
        case EXPLICITLY_INCOMPLETE:
            return false;
        case DOCUMENT_COUNT:
            return docs == active;
        default:
            throw new IllegalStateException("Implementation out of sync. Please report this as a bug.");
        }
    }

    /**
     * @return the number of search instances which participated in the search.
     */
    public int getNodes() {
        return nodes;
    }

    /**
     * A Coverage instance contains coverage information for potentially more
     * than one search. If several queries, e.g. through blending of results
     * from multiple clusters, produced a result set, this number will show how
     * many of the result sets for these queries had full coverage.
     *
     * @return the number of result sets which had full coverage
     */
    public int getFullResultSets() {
        return fullResultSets;
    }

    /**
     * A Coverage instance contains coverage information for potentially more
     * than one search. If several queries, e.g. through blending of results
     * from multiple clusters, produced a result set, this number will show how
     * many result sets containing coverage information this Coverage instance
     * contains information about.
     *
     * @return the number of result sets with coverage information for this instance
     */
    public int getResultSets() {
        return resultSets;
    }

    /**
     * An int between 0 (inclusive) and 100 (inclusive) representing how many
     * percent coverage the result sets this Coverage instance contains information
     * about had.
     */
    public int getResultPercentage() {
        if (getResultSets() == 0) {
            return 0;
        }
        if (docs < active) {
            return (int) Math.round(docs * 100.0d / active);
        }
        return getFullResultSets() * 100 / getResultSets();
    }

}
