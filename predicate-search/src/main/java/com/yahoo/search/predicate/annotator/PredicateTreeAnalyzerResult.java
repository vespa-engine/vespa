// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.annotator;

import com.yahoo.document.predicate.Predicate;

import java.util.Map;

/**
 * Holds the results from {@link com.yahoo.search.predicate.annotator.PredicateTreeAnalyzer#analyzePredicateTree(com.yahoo.document.predicate.Predicate)}.
 *
 * @author bjorncs
 */
public class PredicateTreeAnalyzerResult {

    public final int minFeature;
    public final int treeSize;
    public final Map<Predicate, Integer> sizeMap;

    public PredicateTreeAnalyzerResult(int minFeature, int treeSize, Map<Predicate, Integer> sizeMap) {
        this.minFeature = minFeature;
        this.treeSize = treeSize;
        this.sizeMap = sizeMap;
    }
}
