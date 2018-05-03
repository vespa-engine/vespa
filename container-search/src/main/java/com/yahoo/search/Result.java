// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search;

import com.yahoo.collections.ListMap;
import com.yahoo.net.URI;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.result.*;
import com.yahoo.search.statistics.ElapsedTime;

import java.util.Iterator;

/**
 * The Result contains all the data produced by executing a Query: Some very limited global information, and
 * a single HitGroup containing hits of the result. The HitGroup may contain Hits, which are the individual
 * result items, as well as further HitGroups, making up a <i>composite</i> structure. This allows the hits of a result
 * to be hierarchically organized. A Hit is polymorphic and may contain any kind of information deemed
 * an approriate partial answer to the Query.
 *
 * @author bratseth
 */
public final class Result extends com.yahoo.processing.Response implements Cloneable {

    // Note to developers: If you think you should add something here you are probably wrong
    // To add some new kind of data, create a Hit subclass carrying the data and add that instead

    /** The top level hit group of this result */
    private HitGroup hits;

    /** The estimated total number of hits which would in theory be displayed this result is a part of */
    private long totalHitCount;

    /**
     * The estimated total number of <i>deep</i> hits, which includes every object which matches the query.
     * This is always at least the same as totalHitCount. A lower value will cause hitCount to be returned.
     */
    private long deepHitCount;

    /** The time spent producing this result */
    private ElapsedTime timeAccountant = new ElapsedTime();

    /** Coverage information for this result. */
    private Coverage coverage = null;

    /**
     * Headers containing "envelope" meta information to be returned with this result.
     * Used for HTTP getHeaders when the return protocol is HTTP.
     */
    private ListMap<String,String> headers = null;

    /**
     * Result rendering infrastructure.
     */
    private final Templating templating;

    /** Creates a new Result where the top level hit group has id "toplevel" */
    public Result(Query query) {
        this(query, new HitGroup("toplevel"));
    }

    /**
     * Create an empty result.
     * A source creating a result is <b>required</b> to call
     * {@link #setTotalHitCount} before releasing this result.
     *
     * @param query the query which produced this result
     * @param hits the hit container which this will return from {@link #hits()}
     */
    @SuppressWarnings("deprecation")
    public Result(Query query, HitGroup hits) {
        super(query);
        if (query==null) throw new NullPointerException("The query reference in a result cannot be null");
        this.hits=hits;
        hits.setQuery(query);
        if (query.getRanking().getSorting() != null) {
            setHitOrderer(new HitSortOrderer(query.getRanking().getSorting()));
        }
        templating = new Templating(this);
    }

    /** Create a result containing an error */
    public Result(Query query, ErrorMessage errorMessage) {
        this(query);
        hits.addError(errorMessage);
    }

    /**
     * Merges <b>meta information</b> from a result into this.
     * This does not merge hits, but the other information associated
     * with a result. It should <b>always</b> be called when adding
     * hits from a result, but there is no constraints on the order of the calls.
     */
    @SuppressWarnings("deprecation")
    public void mergeWith(Result result) {
        if (templating.usesDefaultTemplate())
            templating.setRenderer(result.templating.getRenderer());
        totalHitCount += result.getTotalHitCount();
        deepHitCount += result.getDeepHitCount();
        timeAccountant.merge(result.getElapsedTime());
        boolean create=true;
        if (result.getCoverage(!create) != null || getCoverage(!create) != null)
            getCoverage(create).merge(result.getCoverage(create));
    }

    /**
     * Merges meta information produced when a Hit already
     * contained in this result has been filled using another
     * result as an intermediary. @see mergeWith(Result) mergeWith.
     */
    public void mergeWithAfterFill(Result result) {
        timeAccountant.merge(result.getElapsedTime());
    }

    /**
     * Returns the number of hit objects available in the top level group of this result.
     * Note that this number is allowed to be higher than the requested number
     * of hits, because a searcher is allowed to add <i>meta</i> hits as well
     * as the requested number of concrete hits.
     */
    public int getHitCount() {
        return hits.size();
    }

    /**
     * <p>Returns the total number of concrete hits contained (directly or in subgroups) in this result.
     * This should equal the requested hits count if the query has that many matches.</p>
     */
    public int getConcreteHitCount() {
        return hits.getConcreteSize();
    }

    /**
     * Returns the <b>estimated</b> total number of concrete hits which would be returned for this query.
     */
    public long getTotalHitCount() {
        return totalHitCount;
    }

    /**
     * Returns the estimated total number of <i>deep</i> hits, which includes every object which matches the query.
     * This is always at least the same as totalHitCount. A lower value will cause hitCount to be returned.
     */
    public long getDeepHitCount() {
        if (deepHitCount<totalHitCount) return totalHitCount;
        return deepHitCount;
    }


    /** Sets the estimated total number of hits this result is a subset of */
    public void setTotalHitCount(long totalHitCount) {
        this.totalHitCount = totalHitCount;
    }

    /** Sets the estimated total number of deep hits this result is a subset of */
    public void setDeepHitCount(long deepHitCount) {
        this.deepHitCount = deepHitCount;
    }

    public ElapsedTime getElapsedTime() {
        return timeAccountant;
    }

    public void setElapsedTime(ElapsedTime t) {
        timeAccountant = t;
    }

    /**
     * Returns true only if _all_ hits in this result originates from a cache.
     */
    public boolean isCached() {
        return hits.isCached();
    }

    /**
     * Returns whether all hits in this result have been filled with
     * the properties contained in the given summary class. Note that
     * this method will also return true if no hits in this result are
     * fillable.
     */
    public boolean isFilled(String summaryClass) {
        return hits.isFilled(summaryClass);
    }

    /** Returns the query which produced this result */
    public Query getQuery() { return hits.getQuery(); }

    /** Sets a query for this result */
    public void setQuery(Query query) { hits.setQuery(query); }

    /**
     * <p>Sets the hit orderer to be used for the top level hit group.</p>
     *
     * @param hitOrderer the new hit orderer, or null to use default relevancy ordering
     */
    public void setHitOrderer(HitOrderer hitOrderer) { hits.setOrderer(hitOrderer); }

    /** Returns the orderer used by the top level group, or null if the default relevancy order is used */
    public HitOrderer getHitOrderer() { return hits.getOrderer(); }

    public void setDeletionBreaksOrdering(boolean flag) { hits.setDeletionBreaksOrdering(flag); }

    public boolean getDeletionBreaksOrdering() { return hits.getDeletionBreaksOrdering(); }

    /** Update cached and filled by iterating through the hits of this result */
    public void analyzeHits() { hits.analyze(); }

    /** Returns the top level hit group containing all the hits of this result */
    public HitGroup hits() { return hits; }

    @Override
    public com.yahoo.processing.response.DataList<?> data() {
        return hits;
    }


    /** Sets the top level hit group containing all the hits of this result */
    public void setHits(HitGroup hits) {
        Validator.ensureNotNull("The top-level hit group of " + this,hits);
        this.hits=hits;
    }

    /**
     * Deep clones this result - copies are made of all hits and subgroups of hits,
     * <i>but not of the query referenced by this</i>.
     */
    public Result clone() {
        Result resultClone = (Result) super.clone();

        resultClone.hits = hits.clone();

        resultClone.getTemplating().setRenderer(null); // TODO: Kind of wrong
        resultClone.setElapsedTime(new ElapsedTime());
        return resultClone;
    }


    public String toString() {
        if (hits.getError() != null) {
            return "Result: " + hits.getErrorHit().errors().iterator().next();
        } else {
            return "Result (" + getConcreteHitCount() + " of total " + getTotalHitCount() + " hits)";
        }
    }

    /**
     * Adds a context message to this query containing the entire content of this result,
     * if tracelevel is 5 or more.
     *
     * @param name the name of the searcher instance returning this result
     */
    public void trace(String name) {
        if (hits().getQuery().getTraceLevel() < 5) {
            return;
        }
        StringBuilder hitBuffer = new StringBuilder(name);

        hitBuffer.append(" returns:\n");
        int counter = 0;

        for (Iterator<Hit> i = hits.unorderedIterator(); i.hasNext();) {
            Hit hit = i.next();

            if (hit.isMeta()) continue;

            hitBuffer.append("  #: ");
            hitBuffer.append(counter);

            traceExtraHitProperties(hitBuffer, hit);

            hitBuffer.append(", relevancy: ");
            hitBuffer.append(hit.getRelevance());

            hitBuffer.append(", source: ");
            hitBuffer.append(hit.getSource());

            hitBuffer.append(", uri: ");
            URI uri = hit.getId();

            if (uri != null) {
                hitBuffer.append(uri.getHost());
            } else {
                hitBuffer.append("(no uri)");
            }
            hitBuffer.append("\n");
            counter++;
        }
        if (counter == 0) {
            hitBuffer.append("(no hits)\n");
        }
        hits.getQuery().trace(hitBuffer.toString(), false, 5);
    }

    /**
     * For tracing custom properties of a hit, see trace(String). An example of
     * using this is in com.yahoo.prelude.Result.
     *
     * @param hitBuffer
     *                the render target
     * @param hit
     *                the hit to be analyzed
     */
    protected void traceExtraHitProperties(StringBuilder hitBuffer, Hit hit) {
        return;
    }

    /** Returns the context of this result - this is equal to getQuery().getContext(create) */
    public QueryContext getContext(boolean create) { return getQuery().getContext(create); }

    public void setCoverage(Coverage coverage) { this.coverage = coverage; }

    /**
     * Returns coverage information
     *
     * @param create if true the coverage information of this result is created if missing
     * @return the coverage information of this, or null if none and create is false
     */
    public Coverage getCoverage(boolean create) {
        if (coverage == null && create) {
            if (hits.getError() == null) {
                // No error here implies full coverage.
                // Don't count this as a result set if there's no data - avoid counting empty results made
                // to simplify code paths
                coverage = new Coverage(0L, 0, true, (hits().size()==0 ? 0 : 1));
            } else {
                coverage = new Coverage(0L, 0, false);
            }
        }
        return coverage;
    }

    /**
     * Returns the set of "envelope" headers to be returned with this result.
     * This returns the live map in modifiable form - modify this to change the
     * headers. Or null if none, and it should not be created.
     * <p>
     * Used for HTTP headers when the return protocol is HTTP, e.g
     * <pre>result.getHeaders(true).put("Cache-Control","max-age=120")</pre>
     *
     * @param create if true, create the header ListMap if it does not exist
     * @return returns the ListMap of current headers, or null if no headers are set and <pre>create</pre> is false
     */
    public ListMap<String, String> getHeaders(boolean create) {
        if (headers == null && create)
            headers = new ListMap<>();
        return headers;
    }

    /**
     * The Templating object contains helper methods and data containers for
     * result rendering.
     *
     * @return helper object for result rendering
     */
    public Templating getTemplating() {
        return templating;
    }

}
