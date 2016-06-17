// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

/**
 * The coverage report for a result set.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @author baldersheim
 */
public class Coverage extends com.yahoo.container.handler.Coverage {

    public Coverage(long docs, long active) {
        super(docs, active, 0, 1);
    }

    public Coverage(long docs, int nodes, boolean full) {
        this(docs, nodes, full, 1);
    }

    public Coverage(long docs, int nodes, boolean full, int resultSets) {
        super(docs, nodes, full, resultSets);
    }

}
