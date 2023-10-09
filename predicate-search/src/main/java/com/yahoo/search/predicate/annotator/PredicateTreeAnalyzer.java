// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.annotator;

import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.PredicateHash;
import com.yahoo.search.predicate.index.Feature;
import com.yahoo.search.predicate.index.conjunction.IndexableFeatureConjunction;

import java.util.HashMap;
import java.util.Map;

/**
 * This class analyzes a predicate tree to determine two characteristics:
 *  1) The sub-tree size for each conjunction/disjunction node.
 *  2) The min-feature value: a lower bound of the number of term required to satisfy a predicate. This lower bound is
 *     an estimate which is guaranteed to be less than or equal to the real lower bound.
 *
 * @author bjorncs
 */
public class PredicateTreeAnalyzer {

    /**
     * @param predicate The predicate tree.
     * @return a result object containing the min-feature value, the tree size and sub-tree sizes.
     */
    public static PredicateTreeAnalyzerResult analyzePredicateTree(Predicate predicate) {
        AnalyzerContext context = new AnalyzerContext();
        int treeSize = aggregatePredicateStatistics(predicate, false, context);
        int minFeature = ((int)Math.ceil(findMinFeature(predicate, false, context))) + (context.hasNegationPredicate ? 1 : 0);
        return new PredicateTreeAnalyzerResult(minFeature, treeSize, context.subTreeSizes);
    }

    // First analysis pass. Traverses tree in depth-first order. Determines the sub-tree sizes and counts the occurrences
    // of each feature (used by min-feature calculation in second pass).
    // Returns the size of the analyzed subtree.
    private static int aggregatePredicateStatistics(Predicate predicate, boolean isNegated, AnalyzerContext context) {
        if (predicate instanceof Negation) {
            return aggregatePredicateStatistics(((Negation) predicate).getOperand(), !isNegated, context);
        } else if (predicate instanceof Conjunction) {
            return ((Conjunction)predicate).getOperands().stream()
                    .mapToInt(child -> {
                        int size = aggregatePredicateStatistics(child, isNegated, context);
                        context.subTreeSizes.put(child, size);
                        return size;
                    }).sum();
        } else if (predicate instanceof FeatureConjunction) {
            if (isNegated) {
                context.hasNegationPredicate = true;
                return 2;
            }
            // Count the number of identical feature conjunctions - use the id from IndexableFeatureConjunction as key
            IndexableFeatureConjunction ifc = new IndexableFeatureConjunction((FeatureConjunction) predicate);
            incrementOccurrence(context.conjunctionOccurrences, ifc.id);
            // Handled as leaf in interval algorithm - count a single child
            return 1;
        } else if (predicate instanceof Disjunction) {
            return ((Disjunction)predicate).getOperands().stream()
                    .mapToInt(child -> aggregatePredicateStatistics(child, isNegated, context)).sum();
        } else if (predicate instanceof FeatureSet) {
            if (isNegated) {
                context.hasNegationPredicate = true;
                return 2;
            } else {
                FeatureSet featureSet = (FeatureSet) predicate;
                for (String value : featureSet.getValues()) {
                    incrementOccurrence(context.featureOccurrences, Feature.createHash(featureSet.getKey(), value));
                }
                return 1;
            }
        } else if (predicate instanceof FeatureRange) {
            if (isNegated) {
                context.hasNegationPredicate = true;
                return 2;
            } else {
                incrementOccurrence(context.featureOccurrences, PredicateHash.hash64(((FeatureRange) predicate).getKey()));
                return 1;
            }
        } else {
            throw new UnsupportedOperationException("Cannot handle predicate of type " + predicate.getClass().getSimpleName());
        }
    }

    // Second analysis pass. Traverses tree in depth-first order. Determines the min-feature value.
    private static double findMinFeature(Predicate predicate, boolean isNegated, AnalyzerContext context) {
        if (predicate instanceof Conjunction) {
            // Sum of children values.
            return ((Conjunction) predicate).getOperands().stream()
                    .mapToDouble(child -> findMinFeature(child, isNegated, context))
                    .sum();
        } else if (predicate instanceof FeatureConjunction) {
            if (isNegated) {
                return 0.0;
            }
            // The FeatureConjunction is handled as a leaf node in the interval algorithm.
            IndexableFeatureConjunction ifc = new IndexableFeatureConjunction((FeatureConjunction) predicate);
            return 1.0 / context.conjunctionOccurrences.get(ifc.id);
        } else if (predicate instanceof Disjunction) {
            // Minimum value of children.
            return ((Disjunction) predicate).getOperands().stream()
                    .mapToDouble(child -> findMinFeature(child, isNegated, context))
                    .min()
                    .getAsDouble();
        } else if (predicate instanceof Negation) {
            return findMinFeature(((Negation) predicate).getOperand(), !isNegated, context);
        } else if (predicate instanceof FeatureSet) {
            if (isNegated) {
                return 0.0;
            }
            double minFeature = 1.0;
            FeatureSet featureSet = (FeatureSet) predicate;
            for (String value : featureSet.getValues()) {
                long featureHash = Feature.createHash(featureSet.getKey(), value);
                // Clever mathematics to handle scenarios where same feature is used several places in predicate tree.
                minFeature = Math.min(minFeature, 1.0 / context.featureOccurrences.get(featureHash));
            }
            return minFeature;
        } else if (predicate instanceof FeatureRange) {
            if (isNegated) {
                return 0.0;
            }
            return 1.0 / context.featureOccurrences.get(PredicateHash.hash64(((FeatureRange) predicate).getKey()));
        } else {
            throw new UnsupportedOperationException("Cannot handle predicate of type " + predicate.getClass().getSimpleName());
        }
    }

    private static void incrementOccurrence(Map<Long, Integer> featureOccurrences, long featureHash) {
        featureOccurrences.merge(featureHash, 1, Integer::sum);
    }

    // Data structure to hold aggregated data during analysis.
    private static class AnalyzerContext {
        // Mapping from feature hash to occurrence count.
        public final Map<Long, Integer> featureOccurrences = new HashMap<>();
        // Mapping from conjunction id to occurrence count.
        public final Map<Long, Integer> conjunctionOccurrences = new HashMap<>();
        // Mapping from predicate to sub-tree size.
        public final Map<Predicate, Integer> subTreeSizes = new HashMap<>();
        // Does the tree contain any Negation nodes?
        public boolean hasNegationPredicate = false;
    }

}
