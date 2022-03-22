// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.util.Comparator.comparingDouble;
import static java.util.Objects.requireNonNull;

/**
 * A term which contains a set of numerical ranges; a match with any of these indicates a match.
 * <p>
 * This is a stricter version of an {@link OrItem} containing multiple {@link RangeItem}s, where all
 * ranges must be specified against the same field, or pair of fields, and where all the left boundaries,
 * and all the right boundaries, share whether they are {@link Limit#INCLUSIVE} or {@link Limit#EXCLUSIVE}.
 * Furthermore, all overlapping ranges are joined, such that the set of ranges this represents is
 * guaranteed to be non-overlapping, and thus, sorted order of start and end boundaries is the same,
 * which allows efficient filtering of document points or ranges by whether they are included in any of
 * the ranges contained in this term. Note that touching ranges, like [3, 5) and [5, 7), will be joined
 * unless this item is declared to have exclusive boundaries at both ends, or if one of the items is contained
 * within the other, e.g., (3, 5) and (5, 5) (see below for explanation of weirdness).
 * <p>
 * In the absence of a range type in the schema definition, all ranges (and points) contained in documents
 * are taken to be inclusive at both ends. However, intersection of range starts from documents and range
 * ends from the query is inclusive, i.e., matching on equality, iff. both boundaries are inclusive, and
 * likewise for intersection between range starts from query and range ends from documents. It is therefore
 * possible to achieve any matching by choosing inclusiveness for the query ranges properly.
 * For the case where document ranges are to be treated as exclusive, and the query has single points, this
 * becomes weird, since the ranges [1, 1), (1, 1] and (1, 1) are all logically empty, but this still works :)
 *
 * Unless ranges are added in ascending start order, the implementation lazily sorts and merges ranges,
 * when a representation of the item is required. This is typically when the query is serialized and sent
 * to the backend, or when trace information is written, or {@link #toString()} is called on the item.
 * Adding ranges in ascending order is much faster than not; ascending order here has the rather lax
 * requirement that each added interval is not completely before the current last interval.
 *
 * @author jonmv
 */
public class MultiRangeItem extends MultiTermItem {

    public enum Limit {
        /** Points match the boundaries of ranges which are inclusive. */
        INCLUSIVE,
        /** Points do not match the boundaries of ranges which are exclusive. */
        EXCLUSIVE;
    }

    static class Range {

        final Number start;
        final Number end;

        Range(Number start, Number end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Range range = (Range) o;
            return start.equals(range.start) && end.equals(range.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

    }

    private static final Comparator<Number> comparator = comparingDouble(Number::doubleValue);

    private final String startIndex;
    private final String endIndex;
    private final boolean startInclusive;
    private final boolean endInclusive;
    private List<Range> ranges = new ArrayList<>();
    private boolean sorted = true;


    /** A multi range item with ranges to intersect with ranges given by the pair of index names. */
    private MultiRangeItem(String startIndex, Limit startInclusive,
                           String endIndex, Limit endInclusive) {
        if (startIndex.isBlank()) throw new IllegalArgumentException("start index name must be non-blank");
        if (endIndex.isBlank()) throw new IllegalArgumentException("end index name must be non-blank");
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.startInclusive = startInclusive == requireNonNull(Limit.INCLUSIVE);
        this.endInclusive = endInclusive == requireNonNull(Limit.INCLUSIVE);
    }

    /** A multi range item with the given ranges to intersect with the ranges defined by the two given index names. */
    public static MultiRangeItem overRanges(String startIndex, Limit startInclusive, String endIndex, Limit endInclusive) {
        return new MultiRangeItem(startIndex, startInclusive, endIndex, endInclusive);
    }

    /** A multi range item with ranges to contain the points defined by the single given index name. */
    public static MultiRangeItem overPoints(String index, Limit startInclusive, Limit endInclusive) {
        return overRanges(index, startInclusive, index, endInclusive);
    }

    /** Adds the given range to this item. More efficient when each added range is not completely before the current last one. */
    public MultiRangeItem add(Number start, Number end) {
        if (comparator.compare(start, end) > 0)
            throw new IllegalArgumentException("ranges must satisfy start <= end, but got " + start + " > " + end);

        Range range = new Range(start, end);
        // If the list of ranges is still sorted, we try to keep it sorted.
        if (sorted && ! ranges.isEmpty()) {
            // If the new range is not completely before the current last range, we can prune, and keep a sorted list.
            Range last = ranges.get(ranges.size() - 1);
            sorted = overlap(range.end, last.start);
            if (sorted) {
                // Pop all ranges that overlap with the new range ...
                Range popped = range;
                for (int i = ranges.size(); i-- > 0; )
                    if (overlap(ranges.get(i).end, range.start))
                        popped = ranges.remove(i);
                    else
                        break;
                // ... then add a new range, possibly with start and end from the popped and last ranges.
                ranges.add(popped == range ? range : new Range(min(popped.start, range.start), max(last.end, range.end)));
                return this;
            }
        }
        ranges.add(range);
        return this;
    }

    private boolean overlap(Number endOfFirst, Number startOfSecond) {
        int comparison = comparator.compare(endOfFirst, startOfSecond);
        return comparison > 0 || comparison == 0 && startInclusive | endInclusive;
    }

    private Number min(Number a, Number b) {
        return comparator.compare(a, b) <= 0 ? a : b;
    }

    private Number max(Number a, Number b) {
        return comparator.compare(a, b) >= 0 ? a : b;
    }

    private List<Range> sortedRanges() {
        if (sorted)
            return ranges;

        ranges.sort(comparingDouble(range -> range.start.doubleValue()));

        List<Range> pruned = new ArrayList<>();
        Range start = ranges.get(0), end = start;
        for (int i = 1; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            if (overlap(end.end, range.start)) {
                end = comparator.compare(end.end, range.end) < 0 ? range : end;
            }
            else {
                pruned.add(start == end ? start : new Range(start.start, end.end));
                start = end = range;
            }
        }
        pruned.add(start == end ? start : new Range(start.start, end.end));

        sorted = true;
        return ranges = pruned;
    }

    @Override
    public void setIndexName(String index) { } // This makes no sense :<

    @Override
    OperatorType operatorType() {
        return OperatorType.OR;
    }

    @Override
    TermType termType() {
        return TermType.RANGES;
    }

    @Override
    int terms() {
        return sortedRanges().size();
    }

    @Override
    void encodeBlueprint(ByteBuffer buffer) {
        boolean sameIndex = startIndex.equals(endIndex);
        byte metadata = 0;
        if (sameIndex)      metadata |= 0b00000001;
        if (startInclusive) metadata |= 0b00000010;
        if (endInclusive)   metadata |= 0b00000100;
        buffer.put(metadata);
        putString(startIndex, buffer);
        if ( ! sameIndex) putString(endIndex, buffer);
    }

    @Override
    void encodeTerms(ByteBuffer buffer) {
        for (Range range : sortedRanges()) {
            buffer.putDouble(range.start.doubleValue());
            buffer.putDouble(range.end.doubleValue());
        }
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        asCompositeItem().appendBodyString(buffer);
    }

    @Override
    public void disclose(Discloser discloser) {
        asCompositeItem().disclose(discloser);
    }

    @Override
    public Item clone() {
        MultiRangeItem clone = (MultiRangeItem) super.clone();
        clone.ranges = new ArrayList<>(ranges);
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if ( ! super.equals(o)) return false;
        MultiRangeItem that = (MultiRangeItem) o;
        return    startInclusive == that.startInclusive
               && endInclusive == that.endInclusive
               && startIndex.equals(that.startIndex)
               && endIndex.equals(that.endIndex)
               && sortedRanges().equals(that.sortedRanges());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startIndex, endIndex, startInclusive, endInclusive, sortedRanges());
    }

    Item asCompositeItem() {
        OrItem root = new OrItem();
        if (startIndex.equals(endIndex)) {
            for (Range range : sortedRanges()) {
                root.addItem(new IntItem(new com.yahoo.prelude.query.Limit(range.start, startInclusive),
                                         new com.yahoo.prelude.query.Limit(range.end, endInclusive),
                                         startIndex));
            }
        }
        else {
            for (Range range : sortedRanges()) {
                AndItem pair = new AndItem();
                pair.addItem(new IntItem(new com.yahoo.prelude.query.Limit(range.start, startInclusive),
                                         com.yahoo.prelude.query.Limit.POSITIVE_INFINITY,
                                         startIndex));
                pair.addItem(new IntItem(com.yahoo.prelude.query.Limit.NEGATIVE_INFINITY,
                                         new com.yahoo.prelude.query.Limit(range.end, endInclusive),
                                         endIndex));
                root.addItem(pair);
            }
        }
        return root;
    }

}