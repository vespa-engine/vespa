// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.PredicateOperator;
import com.yahoo.document.predicate.RangeEdgePartition;
import com.yahoo.document.predicate.RangePartition;

import static java.lang.Math.abs;

/**
 * Partitions all the feature ranges according to the arity and bounds
 * set in the PredicateOptions, and updates the ranges with a set of
 * partitions and edge partitions.
 * This is required to be able to store a range in the PredicateIndex.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class ComplexNodeTransformer implements PredicateProcessor {

    public void processPredicate(Predicate predicate, PredicateOptions options) {
        if (predicate instanceof PredicateOperator) {
            for (Predicate p : ((PredicateOperator) predicate).getOperands()) {
                processPredicate(p, options);
            }
        } else if (predicate instanceof FeatureRange) {
            processFeatureRange((FeatureRange) predicate, options);
        }
    }

    private void processFeatureRange(FeatureRange range, PredicateOptions options) {
        range.clearPartitions();
        int arity = options.getArity();
        RangePruner rangePruner = new RangePruner(range, options, arity);
        long from = rangePruner.getFrom();
        long to = rangePruner.getTo();

        if (from < 0) {
            if (to < 0) {
                // Special case for to==-1. -X-0 means the same as -X-1, but is more efficient.
                partitionRange(range, (to == -1 ? 0 : -to), -from, arity, true);
            } else {
                partitionRange(range, 0, -from, arity, true);
                partitionRange(range, 0, to, arity, false);
            }
        } else {
            partitionRange(range, from, to, arity, false);
        }
    }

    private void partitionRange(FeatureRange range, long from, long to, int arity, boolean isNeg) {
        int fromRemainder = abs((int) (from % arity));  // from is only negative when using LLONG_MIN.
        // operate on exclusive upper bound.
        int toRemainder = ((int) ((to - arity + 1) % arity) + arity) % arity;  // avoid overflow of (to + 1)
        long fromVal = from - fromRemainder;
        long toVal = to - toRemainder;  // use inclusive upper bound here to avoid overflow problems.
        long fromValDividedByArity = abs(fromVal / arity);
        if (fromVal - 1 == toVal) {  // (toVal + 1) might cause overflow
            addEdgePartition(range, fromVal, fromRemainder, toRemainder - 1, isNeg);
            return;
        } else {
            if (fromRemainder != 0) {
                addEdgePartition(range, fromVal, fromRemainder, -1, isNeg);
                fromValDividedByArity += 1;
            }
            if (toRemainder != 0) {
                addEdgePartition(range, toVal + 1, -1, toRemainder - 1, isNeg);
            }
        }
        makePartitions(range, fromValDividedByArity, abs((toVal - (arity - 1)) / arity) + 1, arity, arity, isNeg);
    }

    private void addEdgePartition(FeatureRange range, long value, int from, int to, boolean isNeg) {
        String label;
        if (value == 0x8000000000000000L)  // special case long_min.
            label = range.getKey() + "=-9223372036854775808";
        else
            label = range.getKey() + (isNeg ? "=-" : "=") + Long.toString(value);
        range.addPartition(new RangeEdgePartition(label, value, from, to));
    }

    private void makePartitions(FeatureRange range, long fromVal, long toVal, long stepSize, int arity, boolean isNeg) {
        int fromRemainder = (int) (fromVal % arity);
        int toRemainder = (int) (toVal % arity);
        long nextFromVal = fromVal - fromRemainder;
        long nextToVal = toVal - toRemainder;
        if (nextFromVal == nextToVal) {
            addPartitions(range, nextFromVal, stepSize, fromRemainder, toRemainder, isNeg);
        } else {
            if (fromRemainder > 0) {
                addPartitions(range, nextFromVal, stepSize, fromRemainder, arity, isNeg);
                fromVal = nextFromVal + arity;
            }
            addPartitions(range, nextToVal, stepSize, 0, toRemainder, isNeg);
            makePartitions(range, fromVal / arity, toVal / arity, stepSize * arity, arity, isNeg);
        }
    }

    private void addPartitions(FeatureRange range, long part, long partSize, int first, int last, boolean isNeg) {
        for (int i = first; i < last; ++i) {
            range.addPartition(new RangePartition(
                    range.getKey(), (part + i) * partSize, (part + i + 1) * partSize - 1, isNeg));
        }
    }

    @Override
    public Predicate process(Predicate predicate, PredicateOptions options) {
        processPredicate(predicate, options);
        return predicate;
    }

    /**
     * Prunes ranges that lie partially or completely outside the configured upper/lower bounds.
     */
    private static class RangePruner {
        private long from;
        private long to;

        public RangePruner(FeatureRange range, PredicateOptions options, int arity) {
            from = range.getFromInclusive() != null ? range.getFromInclusive() : options.getAdjustedLowerBound();
            to = range.getToInclusive() != null ? range.getToInclusive() : options.getAdjustedUpperBound();

            if (from > options.getUpperBound()) {  // Range completely beyond bounds
                long upperRangeStart = Long.MAX_VALUE - (Long.MAX_VALUE % arity) - arity;
                if (options.getUpperBound() < upperRangeStart) {
                    from = upperRangeStart;
                    to = upperRangeStart + arity - 1;
                } else {
                    to = from;
                }
            } else if (to < options.getLowerBound()) {  // Range completely before bounds
                long lowerRangeEnd = Long.MIN_VALUE + (arity - (Long.MIN_VALUE % arity));
                if (options.getLowerBound() > lowerRangeEnd) {
                    from = lowerRangeEnd - arity + 1;
                    to = lowerRangeEnd;
                } else {
                    from = to;
                }
            } else {  // Modify if range overlaps bounds
                if (from < options.getLowerBound()) {
                    from = options.getAdjustedLowerBound();
                }
                if (to > options.getUpperBound()) {
                    to = options.getAdjustedUpperBound();
                }
            }
        }

        public long getFrom() {
            return from;
        }

        public long getTo() {
            return to;
        }
    }

}
