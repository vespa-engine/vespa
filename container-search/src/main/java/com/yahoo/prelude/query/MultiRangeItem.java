// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
 * possible to achieve any matching by choosing inclusiveness for the query ranges appropriately.
 * For the case where document ranges are to be treated as exclusive, and the query has single points, this
 * becomes weird, since the ranges [1, 1), (1, 1] and (1, 1) are all logically empty, but this still works :)
 * <p>
 * Unless ranges are added in ascending start order, the implementation lazily sorts and merges ranges,
 * when a representation of the item is required. This is typically when the query is serialized and sent
 * to the backend, or when trace information is written, or {@link #toString()} is called on the item.
 * Adding ranges in ascending order is much faster than not; ascending order here has the rather lax
 * requirement that each added interval is not completely before the current last interval.
 *
 * @author jonmv
 */
public class MultiRangeItem<Type extends Number> extends MultiTermItem {

    public enum Limit {
        /** Points match the boundaries of ranges which are inclusive. */
        INCLUSIVE,
        /** Points do not match the boundaries of ranges which are exclusive. */
        EXCLUSIVE;
    }

    /** Type of numbers stored used to describe the ranges stored in a {@link MultiRangeItem}. */
    public static class NumberType<Type extends Number> {

        public static final NumberType<Integer> INTEGER = new NumberType<>((a, b) -> a < b ? -1 : a > b ? 1 : 0, ranges ->
                switch (IntegerCompressor.compressionMode(ranges.get(0).start, ranges.get(ranges.size() - 1).end)) {
                    case NONE -> NumberEncoder.UNIFORM_INTEGER;
                    case COMPRESSED -> NumberEncoder.COMPRESSED_INTEGER;
                    case COMPRESSED_POSITIVE -> NumberEncoder.COMPRESSED_POSITIVE_INTEGER;
                });
        public static final NumberType<Long>    LONG    = new NumberType<>((a, b) -> a < b ? -1 : a > b ? 1 : 0, __ -> NumberEncoder.UNIFORM_LONG);
        public static final NumberType<Double>  DOUBLE  = new NumberType<>((a, b) -> a < b ? -1 : a > b ? 1 : 0, __ -> NumberEncoder.UNIFORM_DOUBLE);

        private final Comparator<Type> comparator;
        private final Function<List<Range<Type>>, NumberEncoder<Type>> encoder;

        private NumberType(Comparator<Type> comparator, Function<List<Range<Type>>, NumberEncoder<Type>> encoder) {
            this.comparator = comparator;
            this.encoder = encoder;
        }

        NumberEncoder<Type> encoderFor(List<Range<Type>> ranges) {
            return encoder.apply(ranges);
        }

    }

    static class Range<Type extends Number> {

        final Type start;
        final Type end;

        Range(Type start, Type end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Range<?> range = (Range<?>) o;
            return start.equals(range.start) && end.equals(range.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return "(" + start + ", " + end + ")";
        }

    }

    private static class NumberEncoder<Type extends Number> {

        static NumberEncoder<Integer> UNIFORM_INTEGER             = new NumberEncoder<>(0, ByteBuffer::putInt);
        static NumberEncoder<Long>    UNIFORM_LONG                = new NumberEncoder<>(1, ByteBuffer::putLong);
        static NumberEncoder<Double>  UNIFORM_DOUBLE              = new NumberEncoder<>(2, ByteBuffer::putDouble);
        static NumberEncoder<Integer> COMPRESSED_INTEGER          = new NumberEncoder<>(3, (b, n) -> IntegerCompressor.putCompressedNumber(n, b));
        static NumberEncoder<Integer> COMPRESSED_POSITIVE_INTEGER = new NumberEncoder<>(4, (b, n) -> IntegerCompressor.putCompressedPositiveNumber(n, b));

        final byte id; // 5 bits
        final BiConsumer<ByteBuffer, Type> serializer;

        NumberEncoder(int id, BiConsumer<ByteBuffer, Type> serializer) {
            this.id = (byte) id;
            this.serializer = serializer;
        }

    }

    private final NumberType<Type> type;
    private final String startIndex;
    private final String endIndex;
    private final boolean startInclusive;
    private final boolean endInclusive;
    private List<Range<Type>> ranges = new ArrayList<>();
    private boolean sorted = true;
    private NumberEncoder<Type> encoder;

    /** A multi range item with ranges to intersect with ranges given by the pair of index names. */
    private MultiRangeItem(NumberType<Type> type, String startIndex, Limit startInclusive, String endIndex, Limit endInclusive) {
        if (startIndex.isBlank()) throw new IllegalArgumentException("start index name must be non-blank");
        if (endIndex.isBlank()) throw new IllegalArgumentException("end index name must be non-blank");
        this.type = requireNonNull(type);
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.startInclusive = startInclusive == requireNonNull(Limit.INCLUSIVE);
        this.endInclusive = endInclusive == requireNonNull(Limit.INCLUSIVE);
    }

    /** A multi range item to intersect with the ranges defined by the two given index names. */
    public static <Type extends Number> MultiRangeItem<Type> overRanges(NumberType<Type> type,
                                                                        String startIndex, Limit startInclusive,
                                                                        String endIndex, Limit endInclusive) {
        return new MultiRangeItem<>(type, startIndex, startInclusive, endIndex, endInclusive);
    }

    /** A multi range item to contain the points defined by the single given index name. */
    public static <Type extends Number> MultiRangeItem<Type> overPoints(NumberType<Type> type, String index,
                                                                        Limit startInclusive, Limit endInclusive) {
        return overRanges(type, index, startInclusive, index, endInclusive);
    }

    /** Adds the given point to this item. More efficient when each added point is not completely before the current last one. */
    public MultiRangeItem<Type> addPoint(Type point) {
        return addRange(point, point);
    }

    /** Adds the given range to this item. More efficient when each added range is not completely before the current last one. */
    public MultiRangeItem<Type> addRange(Type start, Type end) {
        if (Double.isNaN(start.doubleValue()))
            throw new IllegalArgumentException("range start cannot be NaN");
        if (Double.isNaN(end.doubleValue()))
            throw new IllegalArgumentException("range end cannot be NaN");
        if (type.comparator.compare(start, end) > 0)
            throw new IllegalArgumentException("ranges must satisfy start <= end, but got " + start + " > " + end);

        Range<Type> range = new Range<>(start, end);
        // If the list of ranges is still sorted, we try to keep it sorted.
        if (sorted && ! ranges.isEmpty()) {
            // If the new range is not completely before the current last range, we can prune, and keep a sorted list.
            Range<Type> last = ranges.get(ranges.size() - 1);
            sorted = overlap(range, last);
            if (sorted) {
                // Pop all ranges that overlap with the new range ...
                Range<Type> popped = range;
                for (int i = ranges.size(); i-- > 0; )
                    if (overlap(ranges.get(i), range))
                        popped = ranges.remove(i);
                    else
                        break;
                // ... then add a new range, possibly with start and end from the popped and last ranges.
                ranges.add(popped == range ? range : new Range<>(min(popped.start, range.start), max(last.end, range.end)));
                return this;
            }
        }
        ranges.add(range);
        return this;
    }

    /**
     * Assumption: first.start <= second.start
     * Then there is overlap if
     * first.end  > second.start
     * first.end == second.start AND either of
     *   ranges are inclusive at either end, OR
     *   either range is a (logically empty) point (a, a), since this dominated by (or equal to) the other range.
     */
    private boolean overlap(Range<Type> first, Range<Type> second) {
        int comparison = type.comparator.compare(first.end, second.start);
        return   comparison  > 0
              || comparison == 0 && (startInclusive || endInclusive || first.start.equals(first.end) || second.start.equals(second.end));
    }

    private Type min(Type a, Type b) {
        return type.comparator.compare(a, b) <= 0 ? a : b;
    }

    private Type max(Type a, Type b) {
        return type.comparator.compare(a, b) >= 0 ? a : b;
    }

    List<Range<Type>> sortedRanges() {
        if (sorted)
            return ranges;

        ranges.sort((r1, r2) -> type.comparator.compare(r1.start, r2.start));

        List<Range<Type>> pruned = new ArrayList<>();
        Range<Type> start = ranges.get(0), end = start;
        for (int i = 1; i < ranges.size(); i++) {
            Range<Type> range = ranges.get(i);
            if (overlap(end, range)) {
                end = type.comparator.compare(end.end, range.end) < 0 ? range : end;
            }
            else {
                pruned.add(start == end ? start : new Range<>(start.start, end.end));
                start = end = range;
            }
        }
        pruned.add(start == end ? start : new Range<>(start.start, end.end));

        sorted = true;
        return ranges = pruned;
    }

    @Override
    public void setIndexName(String index) {
        throw new UnsupportedOperationException("index cannot be changed for " + MultiRangeItem.class.getSimpleName());
    }

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

        encoder = type.encoderFor(sortedRanges());
        metadata |= (byte)(encoder.id << 3);

        buffer.put(metadata);
        putString(startIndex, buffer);
        if ( ! sameIndex) putString(endIndex, buffer);
    }

    @Override
    void encodeTerms(ByteBuffer buffer) {
        for (Range<Type> range : sortedRanges()) {
            encoder.serializer.accept(buffer, range.start);
            encoder.serializer.accept(buffer, range.end);
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
    @SuppressWarnings("unchecked")
    public Item clone() {
        MultiRangeItem<Type> clone = (MultiRangeItem<Type>) super.clone();
        clone.ranges = new ArrayList<>(ranges);
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if ( ! super.equals(o)) return false;
        MultiRangeItem<?> that = (MultiRangeItem<?>) o;
        return    type == that.type
               && startInclusive == that.startInclusive
               && endInclusive == that.endInclusive
               && startIndex.equals(that.startIndex)
               && endIndex.equals(that.endIndex)
               && sortedRanges().equals(that.sortedRanges());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type, startIndex, endIndex, startInclusive, endInclusive, sortedRanges());
    }

    @Override
    Item asCompositeItem() {
        OrItem root = new OrItem();
        if (startIndex.equals(endIndex)) {
            for (Range<Type> range : sortedRanges()) {
                root.addItem(new IntItem(new com.yahoo.prelude.query.Limit(range.start, startInclusive),
                                         new com.yahoo.prelude.query.Limit(range.end, endInclusive),
                                         startIndex));
            }
        }
        else {
            for (Range<Type> range : sortedRanges()) {
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
