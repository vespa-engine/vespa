// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

/**
 * Indexed context lookup methods.
 * Any context which implements these methods supports optimizations where map lookups
 * are replaced by indexed lookups.
 *
 * @author bratseth
 */
public interface ContextIndex {

    /** Returns the number of bound variables in this */
    int size();

    /**
     * Returns the index from a name.
     *
     * @throws NullPointerException is this name is not known to this context
     */
    int getIndex(String name);

    Value get(int index);

    double getDouble(int index);

}
