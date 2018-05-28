// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.google.inject.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.Iterator;
import java.util.Map;


/**
 * A searcher which does parametrized collapsing.
 *
 * @author Steinar Knutsen
 */
@SuppressWarnings("deprecation")
@After(PhaseNames.RAW_QUERY)
@Before(PhaseNames.TRANSFORMED_QUERY)
public class FieldCollapsingSearcher extends Searcher {

    private static final CompoundName collapse = new CompoundName("collapse");
    private static final CompoundName collapsefield=new CompoundName("collapsefield");
    private static final CompoundName collapsesize=new CompoundName("collapsesize");
    private static final CompoundName collapseSummaryName=new CompoundName("collapse.summary");

    /** Maximum number of queries to send next searcher */
    private int maxQueries = 4;

    /**
     * The max number of hits that will be preserved per unique
     * value of the collapsing parameter.
     */
    private int defaultCollapseSize;

    /**
     * The factor by which to scale up the requested number of hits
     * from the next searcher in the chain, because collapsing will
     * likely delete many hits.
     */
    private double extraFactor;

    /** Create this searcher using default values for all settings */
    public FieldCollapsingSearcher() {
        this((String) null);
    }

    /**
     * Creates a collapser
     *
     * @param collapseField the default field to collapse on, or null to not collapse as default
     */
    public FieldCollapsingSearcher(String collapseField) {
        this(1, 2.0, collapseField);
    }

    @Inject
    public FieldCollapsingSearcher(QrSearchersConfig config) {
        QrSearchersConfig.Com.Yahoo.Prelude.Searcher.FieldCollapsingSearcher
                s = config.com().yahoo().prelude().searcher().FieldCollapsingSearcher();

        init(s.collapsesize(), s.extrafactor());
    }

    /**
     * Creates a collapser
     *
     * @param collapseSize the maximum number of hits to keep per
     *        field the default max number of hits in each collapsed group
     * @param extraFactor the percentage by which to scale up the
     *        requested number of hits, to allow some hits to be removed
     *        without refetching
     * @param collapseField the field to collapse on. This is currently <b>ignored</b>.
     */
    public FieldCollapsingSearcher(int collapseSize, double extraFactor, String collapseField) {
        init(collapseSize, extraFactor);
    }

    private void init(int collapseSize, double extraFactor) {
        this.defaultCollapseSize = collapseSize;
        this.extraFactor = extraFactor;
    }

    /**
     * First fetch result from the next searcher in the chain.
     * If collapse is active, do collapsing.
     * Otherwise, act as a simple pass through
     */
    public Result search(com.yahoo.search.Query query, Execution execution) {
        String collapseField = query.properties().getString(collapsefield);

        if (collapseField==null) return execution.search(query);

        int collapseSize = query.properties().getInteger(collapsesize,defaultCollapseSize);
        query.properties().set(collapse, "0");

        int hitsToRequest = query.getHits() != 0 ? (int) Math.ceil((query.getOffset() + query.getHits() + 1) * extraFactor) : 0;
        int nextOffset = 0;
        int hitsAfterCollapse;
        boolean moreHitsAvailable = true;
        Map<String, Integer> knownCollapses = new java.util.HashMap<>();
        Result result = new Result(query);
        int performedQueries = 0;
        Result resultSource;
        String collapseSummary = query.properties().getString(collapseSummaryName);

        do {
            resultSource = search(query.clone(), execution, nextOffset, hitsToRequest);
            String summaryClass = (collapseSummary == null)
                                  ? query.getPresentation().getSummary() : collapseSummary;
            fill(resultSource, summaryClass, execution);
            collapse(result, knownCollapses, resultSource, collapseField, collapseSize);

            hitsAfterCollapse = result.getHitCount();
            if (resultSource.getTotalHitCount() < (hitsToRequest + nextOffset)) {
                // the searcher downstream has no more hits
                moreHitsAvailable = false;
            }
            nextOffset += hitsToRequest;
            if (hitsAfterCollapse < query.getOffset() + query.getHits()) {
                hitsToRequest = (int) Math.ceil(hitsToRequest * extraFactor);
            }
            ++performedQueries;

        } while (hitsToRequest != 0
                && (hitsAfterCollapse < query.getOffset() + query.getHits())
                && moreHitsAvailable
                && (performedQueries <= maxQueries));

        // Set correct meta information
        result.mergeWith(resultSource);
        // Keep only (offset,.. offset+hits) hits
        result.hits().trim(query.getOffset(), query.getHits());
        // Mark query as query with collapsing
        query.properties().set(collapse, "1");
        return result;
    }

    private Result search(Query query, Execution execution, int offset , int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return execution.search(query);
    }

    /**
     * Collapse logic. Preserves only maxHitsPerField hits
     * for each unique value of the collapsing parameter.
     */
    private void collapse(Result result, Map<String, Integer> knownCollapses,
                          Result resultSource, String collapseField, int collapseSize) {
        for (Iterator<Hit> it = resultSource.hits().iterator(); it.hasNext();) {
            Hit unknownHit = it.next();

            if (!(unknownHit instanceof FastHit)) {
                result.hits().add(unknownHit);
                continue;
            }
            FastHit hit = (FastHit) unknownHit;
            Object peek = hit.getField(collapseField);
            String collapseId = peek != null ? peek.toString() : null;
            if (collapseId == null) {
                result.hits().add(hit);
                continue;
            }

            if (knownCollapses.containsKey(collapseId)) {
                int numHitsThisField = knownCollapses.get(collapseId);

                if (numHitsThisField < collapseSize) {
                    result.hits().add(hit);
                    ++numHitsThisField;
                    knownCollapses.put(collapseId, numHitsThisField);
                }
            } else {
                knownCollapses.put(collapseId, 1);
                result.hits().add(hit);
            }
        }
    }

}
