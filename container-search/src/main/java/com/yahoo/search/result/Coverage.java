// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.google.common.annotations.Beta;

/**
 * The coverage report for a result set.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @author baldersheim
 */
public class Coverage extends com.yahoo.container.handler.Coverage {

    public Coverage(long docs, long active) {
        this(docs, active, 0);
    }

    public Coverage(long docs, long active, int nodes) {
        super(docs, active, nodes, 1);
    }

    @Deprecated
    public Coverage(long docs, int nodes, boolean full) {
        this(docs, nodes, full, 1);
    }

    @Deprecated
    public Coverage(long docs, int nodes, boolean full, int resultSets) {
        super(docs, nodes, full, resultSets);
    }

    /**
     * Will set number of documents present in ideal state
     * @param soonActive Number of documents active in ideal state
     * @return self for chaining
     */
    @Beta
    public Coverage setSoonActive(long soonActive) { this.soonActive = soonActive; return this; }

    /**
     * Will set the reasons for degraded coverage as reported by vespa backend.
     * @param degradedReason Reason for degradation
     * @return self for chaining
     */
    public Coverage setDegradedReason(int degradedReason) { this.degradedReason = degradedReason; return this; }

    public Coverage setNodesTried(int nodesTried) { super.setNodesTried(nodesTried); return this; }

}
