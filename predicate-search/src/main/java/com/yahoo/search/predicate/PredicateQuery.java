// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.api.annotations.Beta;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a query in the form of a set of boolean variables that are considered true.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
@Beta
public class PredicateQuery {

    private final ArrayList<Feature> features = new ArrayList<>();
    private final ArrayList<RangeFeature> rangeFeatures = new ArrayList<>();

    /**
     * Adds a feature to the query
     *
     * @param key a feature key
     * @param value a feature value
     */
    public void addFeature(String key, String value) {
        addFeature(key, value, SubqueryBitmap.DEFAULT_VALUE);
    }

    /**
     * Adds a feature to the query, e.g. gender = male.
     *
     * @param key Feature key
     * @param value Feature value
     * @param subqueryBitMap The subquery bitmap for which this term is true
     */
    public void addFeature(String key, String value, long subqueryBitMap) {
        features.add(new Feature(key, value, subqueryBitMap));
    }

    public void addRangeFeature(String key, long value) { addRangeFeature(key, value, SubqueryBitmap.DEFAULT_VALUE);}

    /**
     * Adds a range feature to the query, e.g. age = 25.
     *
     * @param key a feature key
     * @param value a feature value
     * @param subqueryBitMap the subquery bitmap for which this term is true
     */
    public void addRangeFeature(String key, long value, long subqueryBitMap) {
        rangeFeatures.add(new RangeFeature(key, value, subqueryBitMap));
    }

    /** Returns a list of features */
    public List<Feature> getFeatures() { return features; }

    /** Returns a list of range features */
    public List<RangeFeature> getRangeFeatures() { return rangeFeatures; }

    public static class Feature {

        public final String key;
        public final String value;
        public final long subqueryBitmap;
        public final long featureHash;

        public Feature(String key, String value, long subqueryBitmap) {
            this.featureHash = com.yahoo.search.predicate.index.Feature.createHash(key, value);
            this.subqueryBitmap = subqueryBitmap;
            this.value = value;
            this.key = key;
        }

    }

    public static class RangeFeature {

        public final String key;
        public final long value;
        public final long subqueryBitmap;

        public RangeFeature(String key, long value, long subqueryBitmap) {
            this.key = key;
            this.value = value;
            this.subqueryBitmap = subqueryBitmap;
        }

    }

}
