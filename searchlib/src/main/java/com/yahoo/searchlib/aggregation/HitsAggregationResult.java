// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.objects.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This is an aggregated result holding the top n hits for a single group.
 *
 * @author havardpe
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class HitsAggregationResult extends AggregationResult {

    public static final int classId = registerClass(0x4000 + 87, HitsAggregationResult.class);
    private String summaryClass = "default";
    private int maxHits = -1;
    private List<Hit> hits = new ArrayList<>();

    /**
     * Constructs an empty result node.
     */
    public HitsAggregationResult() {
        // empty
    }

    /**
     * Create a hits aggregation result that will collect the given number of hits
     *
     * @param maxHits maximum number of hits to collect
     */
    public HitsAggregationResult(int maxHits) {
        this.maxHits = maxHits;
    }

    /**
     * Create a hits aggregation result that will collect the given number of hits of the summaryClass asked.
     *
     * @param maxHits      maximum number of hits to collect
     * @param summaryClass SummaryClass to use for hits to collect
     */
    public HitsAggregationResult(int maxHits, String summaryClass) {
        this.summaryClass = summaryClass;
        this.maxHits = maxHits;
    }

    /**
     * Obtain the summary class used to collect the hits.
     *
     * @return The summary class id.
     */
    public String getSummaryClass() {
        return summaryClass;
    }

    /**
     * Obtain the maximum number of hits to collect.
     *
     * @return Max number of hits to collect.
     */
    public int getMaxHits() {
        return maxHits;
    }

    /**
     * Sets the summary class of hits to collect.
     *
     * @param summaryClass the summary class to collect.
     * @return this, to allow chaining.
     */
    public HitsAggregationResult setSummaryClass(String summaryClass) {
        this.summaryClass = summaryClass;
        return this;
    }

    /**
     * Sets the maximum number of hits to collect.
     *
     * @param maxHits the number of hits to collect.
     * @return this, to allow chaining.
     */
    public HitsAggregationResult setMaxHits(int maxHits) {
        this.maxHits = maxHits;
        return this;
    }

    /**
     * Obtain the hits collected by this aggregation result
     *
     * @return collected hits
     */
    public List<Hit> getHits() {
        return hits;
    }

    /**
     * Adds a hit to this aggregation result
     *
     * @param h the hit
     * @return this object
     */
    public HitsAggregationResult addHit(Hit h) {
        hits.add(h);
        return this;
    }

    @Override
    public ResultNode getRank() {
        if (hits.isEmpty()) {
            return new FloatResultNode(0);
        }
        return new FloatResultNode(hits.get(0).getRank());
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
        byte[] raw = Utf8.toBytes(summaryClass);
        buf.putInt(null, raw.length);
        buf.put(null, raw);

        buf.putInt(null, maxHits);
        int numHits = hits.size();
        buf.putInt(null, numHits);
        for (Hit h : hits) {
            serializeOptional(buf, h);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
        summaryClass = getUtf8(buf);
        maxHits = buf.getInt(null);
        int numHits = buf.getInt(null);
        for (int i = 0; i < numHits; i++) {
            Hit h = (Hit)deserializeOptional(buf);
            hits.add(h);
        }
    }

    @Override
    protected void onMerge(AggregationResult result) {
        hits.addAll(((HitsAggregationResult)result).hits);
    }

    @Override
    public void postMerge() {
        hits.sort((lhs, rhs) -> -Double.compare(lhs.getRank(), rhs.getRank()));
        if ((maxHits >= 0) && (hits.size() > maxHits)) {
            hits = hits.subList(0, maxHits);
        }
    }

    @Override
    protected boolean equalsAggregation(AggregationResult obj) {
        HitsAggregationResult rhs = (HitsAggregationResult)obj;
        if ( ! summaryClass.equals(rhs.summaryClass)) return false;
        if (maxHits != rhs.maxHits) return false;
        if ( ! hits.equals(rhs.hits)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + summaryClass.hashCode() + maxHits + hits.hashCode();
    }

    @Override
    public HitsAggregationResult clone() {
        HitsAggregationResult obj = (HitsAggregationResult)super.clone();
        obj.summaryClass = summaryClass;
        obj.maxHits = maxHits;
        obj.hits = new ArrayList<Hit>();
        for (Hit hit : hits) {
            obj.hits.add((Hit)hit.clone());
        }
        return obj;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("summaryClass", summaryClass);
        visitor.visit("maxHits", maxHits);
        visitor.visit("hits", hits);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        for (Hit hit : hits) {
            hit.select(predicate, operation);
        }
    }

}
