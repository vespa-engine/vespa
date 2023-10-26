// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

/**
 * A query term. Query terms are equal if they have the same term string.
 *
 * @author  bratseth
 */
public final class QueryTerm {

    private String term;

    private float connectedness = 0.1f;

    private int weight = 100;

    private float significance = 0.1f;

    private float exactness = 1.0f;

    public QueryTerm(String term) {
        this.term=term;
    }

    public QueryTerm(String term,float connectedness) {
        this.term=term;
        this.connectedness=connectedness;
    }

    public void setTerm(String term) { this.term=term; }

    public String getTerm() { return term; }

    /**
     * Returns how connected this term is to the previous term in the query.
     * Default: 0.1. This is always a number between 0 (not connected at all) and 1 (virtually inseparable)
     */
    public float getConnectedness() { return connectedness; }

    public void setConnectedness(float connectedness) { this.connectedness=connectedness; }

    public void setWeight(int weight) { this.weight=weight; }

    public int getWeight() { return weight; }

    /** The significance of this term: 1-term frequency */
    public void setSignificance(float significance) { this.significance=significance; }

    public float getSignificance() { return significance; }

    /** The degree to which this is exactly the term the user specified (1), or a stemmed form (closer to 0) */
    public float getExactness() { return exactness; }

    @Override
    public int hashCode() { return term.hashCode(); }

    @Override
    public boolean equals(Object object) {
        if (! (object instanceof QueryTerm)) return false;

        return this.term.equals(((QueryTerm)object).term);
    }

    @Override
    public String toString() {
        if (connectedness==0.1f) return term;
        return connectedness + ":" + term;
    }

}
