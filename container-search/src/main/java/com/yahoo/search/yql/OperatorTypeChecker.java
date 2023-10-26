// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

/**
 * Check the type of a single argument.
 */
abstract class OperatorTypeChecker {

    protected final Operator parent;
    protected final int idx;

    protected OperatorTypeChecker(Operator parent, int idx) {
        this.parent = parent;
        this.idx = idx;
    }

    public abstract void check(Object argument);

}
