// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

/**
 * The coverage report for a result set.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 */
public class Coverage extends com.yahoo.container.handler.Coverage {

    /**
     * @deprecated Nodes is required element
     */
    @Deprecated
    public Coverage(long docs, long active) {
        // TODO Wonder about this special handling.....
        this(docs, active, (docs > 0) ? 1 : 0, (docs > 0) ? 1: 0);
    }

    public Coverage(long docs, long active, int nodes) {
        this(docs, active, nodes, 1);
    }

    public Coverage(long docs, long active, int nodes, int resultSets) {
        super(docs, active, nodes, resultSets);
    }

    /**
     * Will set number of documents present in ideal state
     *
     * @param targetActive number of documents active in ideal state
     * @return self for chaining
     */
    public Coverage setTargetActive(long targetActive) { this.targetActive = targetActive; return this; }

    /**
     * Will set the reasons for degraded coverage as reported by vespa backend.
     *
     * @param degradedReason reason for degradation
     * @return self for chaining
     */
    public Coverage setDegradedReason(int degradedReason) { this.degradedReason = degradedReason; return this; }

    public Coverage setNodesTried(int nodesTried) { super.setNodesTried(nodesTried); return this; }

}
