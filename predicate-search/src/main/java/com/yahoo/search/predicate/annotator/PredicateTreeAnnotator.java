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
import com.yahoo.document.predicate.RangeEdgePartition;
import com.yahoo.document.predicate.RangePartition;
import com.yahoo.search.predicate.index.Feature;
import com.yahoo.search.predicate.index.conjunction.IndexableFeatureConjunction;
import com.yahoo.search.predicate.index.Interval;
import com.yahoo.search.predicate.index.IntervalWithBounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs the labelling of the predicate tree. The algorithm is based on the label algorithm described in
 * <a href="http://dl.acm.org/citation.cfm?id=1807171">Efficiently evaluating complex boolean expressions</a>.
 *
 * @author bjorncs
 * @see <a href="http://dl.acm.org/citation.cfm?id=1807171">Efficiently evaluating complex boolean expressions</a>
 */
public class PredicateTreeAnnotator {

    private PredicateTreeAnnotator() {}

    /**
     * Labels the predicate tree by constructing an interval mapping for each predicate node in the tree.
     * @param predicate The predicate tree.
     * @return Returns a result object containing the interval mapping and the min-feature value.
     */
    public static PredicateTreeAnnotations createPredicateTreeAnnotations(Predicate predicate) {
        PredicateTreeAnalyzerResult analyzerResult = PredicateTreeAnalyzer.analyzePredicateTree(predicate);
        // The tree size is used as the interval range.
        int intervalEnd = analyzerResult.treeSize;
        AnnotatorContext context = new AnnotatorContext(intervalEnd, analyzerResult.sizeMap);
        assignIntervalLabels(predicate, Interval.INTERVAL_BEGIN, intervalEnd, false, context);
        return new PredicateTreeAnnotations(
                analyzerResult.minFeature, intervalEnd, context.intervals, context.intervalsWithBounds,
                context.featureConjunctions);
    }

    /**
     * Visits the predicate tree in depth-first order and assigns intervals for features in
     * {@link com.yahoo.document.predicate.FeatureSet} and {@link com.yahoo.document.predicate.FeatureRange}.
     */
    private static void assignIntervalLabels(
            Predicate predicate, int begin, int end, boolean isNegated, AnnotatorContext context) {
        // Assumes that all negations happen directly on leaf-nodes.
        // Otherwise, conjunctions and disjunctions must be switched if negated (De Morgan's law).
        if (predicate instanceof Conjunction) {
            List<Predicate> children = ((Conjunction) predicate).getOperands();
            int current = begin;
            for (int i = 0; i < children.size(); i++) {
                Predicate child = children.get(i);
                int subTreeSize = context.subTreeSizes.get(child);
                if (i == children.size() - 1) { // Last child (and sometimes the only one)
                    assignIntervalLabels(child, current, end, isNegated, context);
                    // No need to update/touch current since this is the last child.
                } else if (i == 0) { // First child
                    int next = context.leftNodeLeaves + subTreeSize + 1;
                    assignIntervalLabels(child, current, next - 1, isNegated, context);
                    current = next;
                } else { // Middle children
                    int next = current + subTreeSize;
                    assignIntervalLabels(child, current, next - 1, isNegated, context);
                    current = next;
                }
            }
        } else if (predicate instanceof FeatureConjunction) {
            // Register FeatureConjunction as it was a FeatureSet with a single child.
            // Note: FeatureConjunction should never be negated as AndOrSimplifier will push negations down to
            // the leafs (FeatureSets).
            int zStarEnd = isNegated ? calculateZStarIntervalEnd(end, context) : end;
            IndexableFeatureConjunction indexable = new IndexableFeatureConjunction((FeatureConjunction)predicate);
            int interval = Interval.fromBoundaries(begin, zStarEnd);
            context.featureConjunctions.computeIfAbsent(indexable, (k) -> new ArrayList<>()).add(interval);
            if (isNegated) {
                registerZStarInterval(begin, end, zStarEnd, context);
            }
            context.leftNodeLeaves += 1;
        } else if (predicate instanceof Disjunction) {
            // All OR children will have the same {begin, end} values, and
            // the values will be same as that of the parent OR node
            for (Predicate child : ((Disjunction) predicate).getOperands()) {
                assignIntervalLabels(child, begin, end, isNegated, context);
            }
        } else if (predicate instanceof FeatureSet) {
            FeatureSet featureSet = (FeatureSet) predicate;
            int zStarEnd = isNegated ? calculateZStarIntervalEnd(end, context) : end;
            for (String value : featureSet.getValues()) {
                long featureHash = Feature.createHash(featureSet.getKey(), value);
                int interval = Interval.fromBoundaries(begin, zStarEnd);
                registerFeatureInterval(featureHash, interval, context.intervals);
            }
            if (isNegated) {
                registerZStarInterval(begin, end, zStarEnd, context);
            }
            context.leftNodeLeaves += 1;
        } else if (predicate instanceof Negation) {
            assignIntervalLabels(((Negation) predicate).getOperand(), begin, end, !isNegated, context);
        } else if (predicate instanceof FeatureRange) {
            FeatureRange featureRange = (FeatureRange) predicate;
            int zStarEnd = isNegated ? calculateZStarIntervalEnd(end, context) : end;
            int interval = Interval.fromBoundaries(begin, zStarEnd);
            for (RangePartition partition : featureRange.getPartitions()) {
                long featureHash = PredicateHash.hash64(partition.getLabel());
                registerFeatureInterval(featureHash, interval, context.intervals);
            }
            for (RangeEdgePartition edgePartition : featureRange.getEdgePartitions()) {
                long featureHash = PredicateHash.hash64(edgePartition.getLabel());
                IntervalWithBounds intervalWithBounds = new IntervalWithBounds(
                        interval, (int) edgePartition.encodeBounds());
                registerFeatureInterval(featureHash, intervalWithBounds, context.intervalsWithBounds);
            }
            if (isNegated) {
                registerZStarInterval(begin, end, zStarEnd, context);
            }
            context.leftNodeLeaves += 1;
        } else {
            throw new UnsupportedOperationException(
                    "Cannot handle predicate of type " + predicate.getClass().getSimpleName());
        }
    }

    private static void registerZStarInterval(int begin, int end, int zStarIntervalEnd, AnnotatorContext context) {
        int interval = Interval.fromZStar1Boundaries(begin - 1, zStarIntervalEnd);
        registerFeatureInterval(Feature.Z_STAR_COMPRESSED_ATTRIBUTE_HASH, interval, context.intervals);
        if (end - zStarIntervalEnd != 1) {
            int extraInterval = Interval.fromZStar2Boundaries(end);
            registerFeatureInterval(Feature.Z_STAR_COMPRESSED_ATTRIBUTE_HASH, extraInterval, context.intervals);
        }
        context.leftNodeLeaves += 1;
    }

    private static int calculateZStarIntervalEnd(int end, AnnotatorContext context) {
        if (!context.finalRangeUsed && end == context.intervalEnd) {
            // Extend the first interval to intervalEnd - 1 to get a second Z* interval of size 1.
            context.finalRangeUsed = true;
            return context.intervalEnd - 1;
        }
        return context.leftNodeLeaves + 1;
    }

    private static <T> void registerFeatureInterval(long featureHash, T interval, Map<Long, List<T>> intervals) {
        intervals.computeIfAbsent(featureHash, (k) -> new ArrayList<>()).add(interval);
    }

    // Data structure to hold aggregated data during traversal of the predicate tree.
    private static class AnnotatorContext {
        // End of interval
        public final int intervalEnd;
        // Mapping from feature to a list of intervals.
        public final Map<Long, List<Integer>> intervals = new HashMap<>();
        // Mapping from a range feature to a list of intervals with bounds.
        public final Map<Long, List<IntervalWithBounds>> intervalsWithBounds = new HashMap<>();
        // List of feature conjunctions from predicate
        public final Map<IndexableFeatureConjunction, List<Integer>> featureConjunctions = new HashMap<>();
        // Mapping from predicate to sub-tree size.
        public final Map<Predicate, Integer> subTreeSizes;
        // Number of prior leaf nodes visited.
        public int leftNodeLeaves = 0;
        // Is final interval range used? (Only relevant for Z* interval)
        public boolean finalRangeUsed = false;

        public AnnotatorContext(int intervalEnd, Map<Predicate, Integer> subTreeSizes) {
            this.intervalEnd = intervalEnd;
            this.subTreeSizes = subTreeSizes;
        }
    }

}
