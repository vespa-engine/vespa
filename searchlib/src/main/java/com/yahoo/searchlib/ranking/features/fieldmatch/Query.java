// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

import com.yahoo.searchlib.ranking.features.fieldmatch.QueryTerm;

import java.util.Arrays;

/**
 * A query: An array of the QueryTerms which searches the field we are calculating for,
 * <p>
 * In addition the sum of the term weights of <i>all</i> the query terms can be set
 * explicitly. This allows us to model the matchWeight rank feature of a field as dependent of
 * the weights of all the terms in the query.
 *
 * @author  bratseth
 */
public class Query {

    private QueryTerm[] terms;

    private int totalTermWeight=0;

    private float totalSignificance=0;

    public Query(String query) {
        this(splitQuery(query));
    }

    /** Creates a query with a list of query terms. The query terms are not, and must not be subsequently modified */
    public Query(QueryTerm[] terms) {
        this.terms=terms;

        for (QueryTerm term : terms) {
            totalTermWeight+=term.getWeight();
            totalSignificance+=term.getSignificance();
        }
    }

    private static QueryTerm[] splitQuery(String queryString) {
        String[] queryTerms=queryString.split(" ");
        QueryTerm[] query=new QueryTerm[queryTerms.length];
        for (int i=0; i<query.length; i++)
            query[i]=new QueryTerm(queryTerms[i]);
        return query;
    }

    /** Returns the query terms we are calculating features of */
    public QueryTerm[] getTerms() { return terms; }

    /**
     * Returns the total term weight for this query.
     * This is the sum of the weights of the terms if not set explicitly, or if set explicitly a higher
     * number which also models a query which also has terms going to other indexes.
     */
    public int getTotalTermWeight() { return totalTermWeight; }

    public void setTotalTermWeight(int totalTermWeight) { this.totalTermWeight=totalTermWeight; }

    /**
     * Returns the total term significance for this query.
     * This is the sum of the significance of the terms if not set explicitly, or if set explicitly a higher
     * number which also models a query which also has terms going to other indexes.
     */
    public float getTotalSignificance() { return totalSignificance; }

    public void setTotalSignificance(float totalSignificance) { this.totalSignificance=totalSignificance; }

    public String toString() {
        return "query: " + Arrays.toString(terms);
    }

}
