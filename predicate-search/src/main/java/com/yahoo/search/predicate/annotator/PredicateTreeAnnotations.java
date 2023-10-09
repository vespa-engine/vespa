// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.annotator;

import com.yahoo.search.predicate.index.IntervalWithBounds;
import com.yahoo.search.predicate.index.conjunction.IndexableFeatureConjunction;

import java.util.List;
import java.util.Map;

/**
 * Holds annotations for all the features of a predicate.
 * This is sufficient information to insert the predicate into a PredicateIndex.
 *
 * @author Magnar Nedland
 */
public class PredicateTreeAnnotations {

    public final int minFeature;
    public final int intervalEnd;
    public final Map<Long, List<Integer>> intervalMap;
    public final Map<Long, List<IntervalWithBounds>> boundsMap;
    public final Map<IndexableFeatureConjunction, List<Integer>> featureConjunctions;

    public PredicateTreeAnnotations(
            int minFeature,
            int intervalEnd,
            Map<Long, List<Integer>> intervalMap,
            Map<Long, List<IntervalWithBounds>> boundsMap,
            Map<IndexableFeatureConjunction, List<Integer>> featureConjunctions) {
        this.minFeature = minFeature;
        this.intervalEnd = intervalEnd;
        this.intervalMap = intervalMap;
        this.boundsMap = boundsMap;
        this.featureConjunctions = featureConjunctions;
    }

}
