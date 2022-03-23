// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.MultiRangeItem.Range;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static com.yahoo.prelude.query.MultiRangeItem.Limit.EXCLUSIVE;
import static com.yahoo.prelude.query.MultiRangeItem.Limit.INCLUSIVE;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.util.Comparator.comparingDouble;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author jonmv
 */
public class MultiRangeItemTestCase {

    // We'll add ranges (0, 0), (0, 2), (2, 2), (3, 4), (5, 8), (6, 6), (7, 9), (9, 9), (1, 8), in that order, and in "random" order.
    final List<Range> ranges = List.of(new Range(1, 8), // Make it "unordered", so sorting is delayed.
                                       new Range(0, 0),
                                       new Range(0, 2),
                                       new Range(2, 2),
                                       new Range(3, 4),
                                       new Range(5, 8),
                                       new Range(6, 6),
                                       new Range(9, 9),
                                       new Range(7, 9));

    @Test
    public void testNanIsDisallowed() {
        try {
            MultiRangeItem.overPoints("i", INCLUSIVE, EXCLUSIVE).addPoint(NaN);
            fail("NaN should be disallowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("range start cannot be NaN", e.getMessage());
        }
    }

    @Test
    public void testSpecialValuesWithClosedIntervals() {
        MultiRangeItem item = MultiRangeItem.overPoints("i", INCLUSIVE, INCLUSIVE);
        item.addPoint(0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
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
        MultiRangeItem item = MultiRangeItem.overPoints("i", INCLUSIVE, EXCLUSIVE);
        item.addPoint( 0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
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
        MultiRangeItem item = MultiRangeItem.overPoints("i", EXCLUSIVE, INCLUSIVE);
        item.addPoint( 0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
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
        MultiRangeItem item = MultiRangeItem.overPoints("i", EXCLUSIVE, EXCLUSIVE);
        item.addPoint( 0.0);
        item.addPoint(-0.0);
        assertEquals(List.of(new Range(0.0, 0.0)),
                     item.sortedRanges());

        item.addRange(NEGATIVE_INFINITY, 0);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, 0.0)),
                     item.sortedRanges());

        item.addRange(0, POSITIVE_INFINITY);
        assertEquals(List.of(new Range(NEGATIVE_INFINITY, POSITIVE_INFINITY)),
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
        MultiRangeItem item = MultiRangeItem.overPoints("i", INCLUSIVE, INCLUSIVE);
        ranges.stream().sorted(comparingDouble(range -> range.start.doubleValue()))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesOrderedByStartWithOpenIntervals() {
        MultiRangeItem item = MultiRangeItem.overPoints("i", EXCLUSIVE, EXCLUSIVE);
        ranges.stream().sorted(comparingDouble(range -> range.start.doubleValue()))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesOrderedByEndWithClosedIntervals() {
        MultiRangeItem item = MultiRangeItem.overPoints("i", INCLUSIVE, INCLUSIVE);
        ranges.stream().sorted(comparingDouble(range -> range.end.doubleValue()))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesOrderedByEndWithOpenIntervals() {
        MultiRangeItem item = MultiRangeItem.overPoints("i", EXCLUSIVE, EXCLUSIVE);
        ranges.stream().sorted(comparingDouble(range -> range.end.doubleValue()))
              .forEach(range -> item.addRange(range.start, range.end));

        assertEquals(List.of(new Range(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesUnorderedWithClosedIntervals() {
        MultiRangeItem item = MultiRangeItem.overPoints("i", INCLUSIVE, INCLUSIVE);
        for (Range range : ranges)
            item.addRange(range.start, range.end);

        assertEquals(List.of(new Range(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testAddingRangesUnorderedWithOpenIntervals() {
        MultiRangeItem item = MultiRangeItem.overPoints("i", EXCLUSIVE, EXCLUSIVE);
        for (Range range : ranges)
            item.addRange(range.start, range.end);

        assertEquals(List.of(new Range(0, 9)),
                     item.sortedRanges());
    }

    @Test
    public void testSerialization() {
        ByteBuffer pointsBuffer = ByteBuffer.allocate(25);
        MultiRangeItem pointsItem = MultiRangeItem.overPoints("i", EXCLUSIVE, INCLUSIVE)
                                                  .addRange(NEGATIVE_INFINITY, POSITIVE_INFINITY);
        pointsItem.encode(pointsBuffer);

        ByteBuffer expected = ByteBuffer.allocate(25);
        expected.put((byte) 0b00000111); // ItemType.MULTI_TERM
        expected.put((byte) 0b00100000); // OR << 5, RANGES
        expected.putInt(1);              // term count
        expected.put((byte) 0b00000101); // end inclusive, same index
        expected.put((byte) 1);          // index name length
        expected.put((byte) 'i');        // index name bytes
        expected.putDouble(NEGATIVE_INFINITY);
        expected.putDouble(POSITIVE_INFINITY);

        pointsBuffer.flip();
        expected.flip();
        assertEquals(expected, pointsBuffer);

        ByteBuffer rangesBuffer = ByteBuffer.allocate(59);
        MultiRangeItem rangesItem = MultiRangeItem.overRanges("i", INCLUSIVE, "j", EXCLUSIVE)
                                                  .addPoint(    -0.0)
                                                  .addRange( 1,  2.0)
                                                  .addRange(-1, -0.5);
        rangesItem.encode(rangesBuffer);

        expected = ByteBuffer.allocate(59);
        expected.put((byte) 0b00000111); // ItemType.MULTI_TERM
        expected.put((byte) 0b00100000); // OR << 5, RANGES
        expected.putInt(3);              // term count
        expected.put((byte) 0b00000010); // end inclusive, same index
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
        assertEquals(expected, rangesBuffer);
    }

}
