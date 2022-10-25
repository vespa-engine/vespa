// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.MultiRangeItem.NumberType;
import com.yahoo.prelude.query.MultiRangeItem.Range;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static com.yahoo.compress.IntegerCompressor.putCompressedNumber;
import static com.yahoo.prelude.query.MultiRangeItem.Limit.EXCLUSIVE;
import static com.yahoo.prelude.query.MultiRangeItem.Limit.INCLUSIVE;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingInt;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 */
public class MultiRangeItemTestCase {

    // We'll add ranges (0, 0), (0, 2), (2, 2), (3, 4), (5, 8), (6, 6), (7, 9), (9, 9), (1, 8), in that order, and in "random" order.
    final List<Range<Integer>> ranges = List.of(new Range<>(1, 8), // Make it "unordered", so sorting is delayed.
                                       new Range<>(0, 0),
                                       new Range<>(0, 2),
                                       new Range<>(2, 2),
                                       new Range<>(3, 4),
                                       new Range<>(5, 8),
                                       new Range<>(6, 6),
                                       new Range<>(9, 9),
                                       new Range<>(7, 9));

    @Test
    public void testNanIsDisallowed() {
        try {
            MultiRangeItem.overPoints(NumberType.DOUBLE, "i", INCLUSIVE, EXCLUSIVE).addPoint(NaN);
            fail("NaN should be disallowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("range start cannot be NaN", e.getMessage());
        }
    }

    @Test
    public void testSpecialValuesWithClosedIntervals() {
        MultiRangeItem<Double> item = MultiRangeItem.overPoints(NumberType.DOUBLE, "i", INCLUSIVE, INCLUSIVE);
        item.addPoint(0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range<>(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0.0);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0.0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
                     item.sortedRanges());

        try {
            item.addRange(POSITIVE_INFINITY, NEGATIVE_INFINITY);
            fail("negative ranges should be disallowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("ranges must satisfy start <= end, but got Infinity > -Infinity", e.getMessage());
        }
    }

    @Test
    public void testSpecialValuesWithClosedOpenIntervals() {
        MultiRangeItem<Double> item = MultiRangeItem.overPoints(NumberType.DOUBLE, "i", INCLUSIVE, EXCLUSIVE);
        item.addPoint( 0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range<>(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0.0);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0.0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
                     item.sortedRanges());

        try {
            item.addRange(POSITIVE_INFINITY, NEGATIVE_INFINITY);
            fail("negative ranges should be disallowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("ranges must satisfy start <= end, but got Infinity > -Infinity", e.getMessage());
        }
    }

    @Test
    public void testSpecialValuesWithOpenClosedIntervals() {
        MultiRangeItem<Double> item = MultiRangeItem.overPoints(NumberType.DOUBLE, "i", EXCLUSIVE, INCLUSIVE);
        item.addPoint( 0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range<>(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0.0);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0.0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
                     item.sortedRanges());

        try {
            item.addRange(POSITIVE_INFINITY, NEGATIVE_INFINITY);
            fail("negative ranges should be disallowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("ranges must satisfy start <= end, but got Infinity > -Infinity", e.getMessage());
        }
    }

    @Test
    public void testSpecialValuesWithOpenIntervals() {
        MultiRangeItem<Double> item = MultiRangeItem.overPoints(NumberType.DOUBLE, "i", EXCLUSIVE, EXCLUSIVE);
        item.addPoint( 0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range<>(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0.0);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0.0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range<>(NEGATIVE_INFINITY, 0.0),
                             new Range<>(0.0, POSITIVE_INFINITY)),
                     item.sortedRanges());

        try {
            item.addRange(POSITIVE_INFINITY, NEGATIVE_INFINITY);
            fail("negative ranges should be disallowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("ranges must satisfy start <= end, but got Infinity > -Infinity", e.getMessage());
        }
    }

    @Test
    public void testAddingRangesOrderedByStartWithClosedIntervals() {
        MultiRangeItem<Integer> item = MultiRangeItem.overPoints(NumberType.INTEGER, "i", INCLUSIVE, INCLUSIVE);
        ranges.stream().sorted(comparingInt(range -> range.start))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range<>(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesOrderedByStartWithOpenIntervals() {
        MultiRangeItem<Integer> item = MultiRangeItem.overPoints(NumberType.INTEGER, "i", EXCLUSIVE, EXCLUSIVE);
        ranges.stream().sorted(comparingInt(range -> range.start))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range<>(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesOrderedByEndWithClosedIntervals() {
        MultiRangeItem<Integer> item = MultiRangeItem.overPoints(NumberType.INTEGER, "i", INCLUSIVE, INCLUSIVE);
        ranges.stream().sorted(comparingInt(range -> range.end))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range<>(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesOrderedByEndWithOpenIntervals() {
        MultiRangeItem<Integer> item = MultiRangeItem.overPoints(NumberType.INTEGER, "i", EXCLUSIVE, EXCLUSIVE);
        ranges.stream().sorted(comparingInt(range -> range.end))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range<>(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesUnorderedWithClosedIntervals() {
        MultiRangeItem<Integer> item = MultiRangeItem.overPoints(NumberType.INTEGER, "i", INCLUSIVE, INCLUSIVE);
        for (Range<Integer> range : ranges)
            item.addRange(range.start, range.end);

        assertEquals(List.of(new Range<>(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesUnorderedWithOpenIntervals() {
        MultiRangeItem<Integer> item = MultiRangeItem.overPoints(NumberType.INTEGER, "i", EXCLUSIVE, EXCLUSIVE);
        for (Range<Integer> range : ranges)
            item.addRange(range.start, range.end);

        assertEquals(List.of(new Range<>(0, 9)),
                     item.sortedRanges());
    }

    @Test
    @Disabled
    public void testDoublePointsSerialization() {
        ByteBuffer pointsBuffer = ByteBuffer.allocate(25);
        MultiRangeItem<Double> pointsItem = MultiRangeItem.overPoints(NumberType.DOUBLE, "i", EXCLUSIVE, INCLUSIVE)
                                                          .addRange(NEGATIVE_INFINITY, POSITIVE_INFINITY);
        pointsItem.encode(pointsBuffer);

        ByteBuffer expected = ByteBuffer.allocate(25);
        expected.put((byte) 0b00000111); // ItemType.MULTI_TERM
        expected.put((byte) 0b00100000); // OR << 5, RANGES
        expected.putInt(1);              // term count
        expected.put((byte) 0b00010101); // UNIFORM_DOUBLE << 3, end inclusive, same index
        expected.put((byte) 1);          // index name length
        expected.put((byte) 'i');        // index name bytes
        expected.putDouble(NEGATIVE_INFINITY);
        expected.putDouble(POSITIVE_INFINITY);

        pointsBuffer.flip();
        expected.flip();
        assertArrayEquals(expected.array(), pointsBuffer.array());
        assertEquals(expected, pointsBuffer);
    }

    @Test
    @Disabled
    public void testDoubleRangesSerialization() {
        ByteBuffer rangesBuffer = ByteBuffer.allocate(59);
        MultiRangeItem<Double> rangesItem = MultiRangeItem.overRanges(NumberType.DOUBLE, "i", INCLUSIVE, "j", EXCLUSIVE)
                                                          .addPoint(      -0.0)
                                                          .addRange( 1.0,  2.0)
                                                          .addRange(-1.0, -0.5);
        rangesItem.encode(rangesBuffer);

        ByteBuffer expected = ByteBuffer.allocate(59);
        expected.put((byte) 0b00000111); // ItemType.MULTI_TERM
        expected.put((byte) 0b00100000); // OR << 5, RANGES
        expected.putInt(3);              // term count
        expected.put((byte) 0b00010010); // UNIFORM_DOUBLE, start inclusive, two indexes
        expected.put((byte) 1);          // index name length
        expected.put((byte) 'i');        // index name bytes
        expected.put((byte) 1);          // index name length
        expected.put((byte) 'j');        // index name bytes
        expected.putDouble(-1.0);
        expected.putDouble(-0.5);
        expected.putDouble(-0.0);
        expected.putDouble(-0.0);
        expected.putDouble( 1.0);
        expected.putDouble( 2.0);

        rangesBuffer.flip();
        expected.flip();
        assertArrayEquals(expected.array(), rangesBuffer.array());
        assertEquals(expected, rangesBuffer);
    }

    @Test
    @Disabled
    public void testIntegerRangesSerialization() {
        ByteBuffer rangesBuffer = ByteBuffer.allocate(24);
        MultiRangeItem<Integer> rangesItem = MultiRangeItem.overRanges(NumberType.INTEGER, "start", INCLUSIVE, "end", EXCLUSIVE)
                                                          .addPoint(                0)
                                                          .addRange( 1, (1 << 29) - 1)
                                                          .addRange(-1,            -0);
        rangesItem.encode(rangesBuffer);

        ByteBuffer expected = ByteBuffer.allocate(24);
        expected.put((byte) 0b00000111);              // ItemType.MULTI_TERM
        expected.put((byte) 0b00100000);              // OR << 5, RANGES
        expected.putInt(2);                           // term count
        expected.put((byte) 0b00011010);              // COMPRESSED_INTEGER << 3, start inclusive, two indexes
        expected.put((byte) 5);                       // index name length
        expected.put("start".getBytes(UTF_8));        // index name bytes
        expected.put((byte) 3);                       // index name length
        expected.put("end".getBytes(UTF_8));          // index name bytes
        putCompressedNumber(-1, expected);            // 1 byte
        putCompressedNumber(0, expected);             // 1 byte
        putCompressedNumber(1, expected);             // 1 byte
        putCompressedNumber((1 << 29) - 1, expected); // 4 bytes

        rangesBuffer.flip();
        expected.flip();
        assertArrayEquals(expected.array(), rangesBuffer.array());
        assertEquals(expected, rangesBuffer);
    }

}
