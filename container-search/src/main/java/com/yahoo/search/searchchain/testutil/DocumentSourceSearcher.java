// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.testutil;


import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import com.yahoo.net.URI;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * <p>Implements a document source.  You pass in a query and a Result
 * set.  When this Searcher is called with that query it will return
 * that result set.</p>
 *
 * <p>This supports multi-phase search.</p>
 *
 * <p>To avoid having to add type information for the fields, a quck hack is used to
 * support testing of attribute prefetching.
 * Any field in the configured hits which has a name starting by attribute
 * will be returned when attribute prefetch filling is requested.</p>
 *
 * @author  bratseth
 */
public class DocumentSourceSearcher extends Searcher {

    // using null as name in the API would just be a horrid headache
    public static final String DEFAULT_SUMMARY_CLASS = "default";

    // TODO: update tests to explicitly set hits, so that the default results can be removed entirely.
    private Result defaultFilledResult;

    private Map<Query, Result> completelyFilledResults = new HashMap<>();
    private Map<Query, Result> unFilledResults = new HashMap<>();
    private Map<String, Set<String>> summaryClasses = new HashMap<>();

    private int queryCount;

    public DocumentSourceSearcher() {
        addDefaultResults();
    }

    /**
     * Adds a result which can be searched for and filled.
     * Summary fields starting by "a" are attributes, others are not.
     *
     * @return true when replacing an existing &lt;query, result&gt; pair.
     */
    public boolean addResult(Query query, Result fullResult) {
        Result emptyResult = new Result(query.clone());
        emptyResult.setTotalHitCount(fullResult.getTotalHitCount());
        for (Hit fullHit : fullResult.hits().asList()) {
            Hit emptyHit = fullHit.clone();
            emptyHit.clearFields();
            emptyHit.setFillable();
            emptyHit.setRelevance(fullHit.getRelevance());

            emptyResult.hits().add(emptyHit);
        }
        unFilledResults.put(getQueryKeyClone(query), emptyResult);

        if (completelyFilledResults.put(getQueryKeyClone(query), fullResult.clone()) != null) {
           // TODO: throw exception if the key exists from before, change the method to void
           return true;
        }
        return false;
    }

    public void addSummaryClass(String name, Set<String> fields) {
        summaryClasses.put(name,fields);
    }

    public void addSummaryClassByCopy(String name, Collection<String> fields) {
        addSummaryClass(name, new HashSet<>(fields));
    }

    private void addDefaultResults() {
        Query q = new Query("?query=default");
        Result r = new Result(q);
        // These four used to assign collapseId 1,2,3,4 - re-add that if needed
        r.hits().add(new Hit("http://default-1.html", 0));
        r.hits().add(new Hit("http://default-2.html", 0));
        r.hits().add(new Hit("http://default-3.html", 0));
        r.hits().add(new Hit("http://default-4.html", 0));
        defaultFilledResult = r;
        addResult(q, r);
    }

    @Override
    public Result search(Query query, Execution execution)  {
        queryCount++;
        Result r;
        r = unFilledResults.get(getQueryKeyClone(query));
        if (r == null) {
            r = defaultFilledResult.clone();
        } else {
            r = r.clone();
        }

        r.setQuery(query);
        r.hits().trim(query.getOffset(), query.getHits());
        return r;
    }

    /**
     * Returns a query clone which has offset and hits set to null. This is used by access to
     * the maps using the query as key to achieve lookup independent of offset/hits value
     */
    private Query getQueryKeyClone(Query query) {
        Query key=query.clone();
        key.setWindow(0,0);
        return key;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        Result filledResult;
        filledResult = completelyFilledResults.get(getQueryKeyClone(result.getQuery()));

        if (filledResult == null) {
            filledResult = defaultFilledResult;
        }
        fillHits(filledResult,result,summaryClass);
    }

    private void fillHits(Result filledHits, Result hitsToFill, String summaryClass) {
        Set<String> fieldsToFill = summaryClasses.get(summaryClass);

        if (fieldsToFill == null ) {
            fieldsToFill = summaryClasses.get(DEFAULT_SUMMARY_CLASS);
        }

        for (Hit hitToFill : hitsToFill.hits()) {
            Hit filledHit = getMatchingFilledHit(hitToFill.getId(), filledHits);

            if (filledHit != null) {
                if (fieldsToFill != null) {
                    copyFieldValuesThatExist(filledHit,hitToFill,fieldsToFill);
                } else {
                    // TODO: remove this block and update fieldsToFill above to throw an exception if no appropriate summary class is found
                    for (Map.Entry<String,Object> propertyEntry : filledHit.fields().entrySet()) {
                        hitToFill.setField(propertyEntry.getKey(),
                                propertyEntry.getValue());
                    }
                }
                hitToFill.setFilled(summaryClass == null ? DEFAULT_SUMMARY_CLASS : summaryClass);
            }
        }
        hitsToFill.analyzeHits();
    }

    private Hit getMatchingFilledHit(URI hitToFillId, Result filledHits) {
        Hit filledHit = null;

        for ( Hit filledHitCandidate : filledHits.hits()) {
            if ( hitToFillId == filledHitCandidate.getId() ) {
                filledHit = filledHitCandidate;
                break;
            }
        }
        return filledHit;
    }

    private void copyFieldValuesThatExist(Hit filledHit, Hit hitToFill, Set<String> fieldsToFill) {
        for (String fieldToFill : fieldsToFill ) {
            if ( filledHit.getField(fieldToFill) != null ) {
                hitToFill.setField(fieldToFill, filledHit.getField(fieldToFill));
            }
        }
    }

    /**
     * Returns the number of queries made to this searcher since the last
     * reset. For testing - not reliable if multiple threads makes
     * queries simultaneously
     */
    public int getQueryCount() {
        return queryCount;
    }

    public void resetQueryCount() {
        queryCount=0;
    }
}
