package com.yahoo.searchlib.rankingexpression.evaluation;

/**
 * Indexed context lookup methods
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

}
