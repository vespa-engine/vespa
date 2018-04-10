// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class GroupingTransformTestCase {

    private static final int REQUEST_ID = 0;

    @Test
    public void requireThatLabelCanBeSet() {
        GroupingTransform transform = newTransform();
        transform.putLabel(0, 1, "foo", "my_type");
        assertEquals("foo", transform.getLabel(1));
    }

    @Test
    public void requireThatLabelCanNotBeReplaced() {
        GroupingTransform transform = newTransform();
        transform.putLabel(0, 1, "foo", "my_type");
        try {
            transform.putLabel(0, 1, "bar", "my_type");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Can not set label of my_type 1 to 'bar' because it is already set to 'foo'.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatLabelIsUniqueAmongSiblings() {
        GroupingTransform transform = newTransform();
        transform.putLabel(0, 1, "foo", "my_type");
        try {
            transform.putLabel(0, 2, "foo", "my_type");
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals("Can not use my_type label 'foo' for multiple siblings.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatMaxDefaultsToZero() {
        GroupingTransform transform = newTransform();
        assertEquals(0, transform.getMax(6));
        assertEquals(0, transform.getMax(9));
    }

    @Test
    public void requireThatMaxCanBeSet() {
        GroupingTransform transform = newTransform();
        transform.putMax(0, 69, "my_type");
        assertEquals(69, transform.getMax(0));
    }

    @Test
    public void requireThatMaxCanNotBeReplaced() {
        GroupingTransform transform = newTransform();
        transform.putMax(0, 6, "my_type");
        try {
            transform.putMax(0, 9, "my_type");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Can not set max of my_type 0 to 9 because it is already set to 6.",
                         e.getMessage());
        }
        assertEquals(6, transform.getMax(0));
    }

    @Test
    public void requireThatOffsetDefaultsToZero() {
        GroupingTransform transform = newTransform();
        assertEquals(0, transform.getOffset(6));
        assertEquals(0, transform.getOffset(9));
    }

    @Test
    public void requireThatOffsetContinuationsCanBeAdded() {
        GroupingTransform transform = newTransform();
        transform.addContinuation(newStableOffset(newResultId(), 6, 9));
        assertEquals(9, transform.getOffset(6));
    }

    @Test
    public void requireThatOffsetByIdCanBeReplaced() {
        GroupingTransform transform = newTransform();
        ResultId id = newResultId(6, 9);
        transform.addContinuation(newStableOffset(id, 0, 6));
        assertEquals(6, transform.getOffset(id));
        transform.addContinuation(newStableOffset(id, 0, 69));
        assertEquals(69, transform.getOffset(id));
        transform.addContinuation(newStableOffset(id, 0, 9));
        assertEquals(9, transform.getOffset(id));
        transform.addContinuation(newStableOffset(id, 0, 96));
        assertEquals(96, transform.getOffset(id));
    }

    @Test
    public void requireThatOffsetByTagEqualsHighestSibling() {
        GroupingTransform transform = newTransform();
        transform.addContinuation(newStableOffset(newResultId(1), 69, 6));
        assertEquals(6, transform.getOffset(69));
        transform.addContinuation(newStableOffset(newResultId(2), 69, 69));
        assertEquals(69, transform.getOffset(69));
        transform.addContinuation(newStableOffset(newResultId(3), 69, 9));
        assertEquals(69, transform.getOffset(69));
        transform.addContinuation(newStableOffset(newResultId(4), 69, 96));
        assertEquals(96, transform.getOffset(69));
    }

    @Test
    public void requireThatOffsetContinuationsCanBeReplaced() {
        GroupingTransform transform = newTransform();
        ResultId id = newResultId(6, 9);
        transform.addContinuation(newStableOffset(id, 1, 1));
        assertEquals(1, transform.getOffset(1));
        assertEquals(1, transform.getOffset(id));
        assertTrue(transform.isStable(id));

        transform.addContinuation(newUnstableOffset(id, 1, 2));
        assertEquals(2, transform.getOffset(1));
        assertEquals(2, transform.getOffset(id));
        assertFalse(transform.isStable(id));

        transform.addContinuation(newStableOffset(id, 1, 3));
        assertEquals(3, transform.getOffset(1));
        assertEquals(3, transform.getOffset(id));
        assertTrue(transform.isStable(id));
    }

    @Test
    public void requireThatUnstableOffsetsAreTracked() {
        GroupingTransform transform = newTransform();
        ResultId stableId = newResultId(6);
        transform.addContinuation(newStableOffset(stableId, 1, 1));
        assertTrue(transform.isStable(stableId));
        ResultId unstableId = newResultId(9);
        transform.addContinuation(newUnstableOffset(unstableId, 2, 3));
        assertTrue(transform.isStable(stableId));
        assertFalse(transform.isStable(unstableId));
    }

    @Test
    public void requireThatCompositeContinuationsAreDecomposed() {
        GroupingTransform transform = newTransform();
        transform.addContinuation(new CompositeContinuation()
                                          .add(newStableOffset(newResultId(), 6, 9))
                                          .add(newStableOffset(newResultId(), 9, 6)));
        assertEquals(9, transform.getOffset(6));
        assertEquals(6, transform.getOffset(9));
    }

    @Test
    public void requireThatUnsupportedContinuationsCanNotBeAdded() {
        GroupingTransform transform = newTransform();
        try {
            transform.addContinuation(new Continuation() {

            });
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatUnrelatedContinuationsAreIgnored() {
        GroupingTransform transform = new GroupingTransform(REQUEST_ID);
        ResultId id = ResultId.valueOf(REQUEST_ID + 1, 1);
        transform.addContinuation(new OffsetContinuation(id, 2, 3, OffsetContinuation.FLAG_UNSTABLE));
        assertEquals(0, transform.getOffset(2));
        assertEquals(0, transform.getOffset(id));
        assertTrue(transform.isStable(id));
    }

    @Test
    public void requireThatToStringIsVerbose() {
        GroupingTransform transform = new GroupingTransform(REQUEST_ID);
        transform.putLabel(1, 1, "label1", "type1");
        transform.putLabel(2, 2, "label2", "type2");
        transform.addContinuation(newStableOffset(ResultId.valueOf(REQUEST_ID), 3, 3));
        transform.addContinuation(newStableOffset(ResultId.valueOf(REQUEST_ID), 4, 4));
        transform.putMax(5, 5, "type5");
        transform.putMax(6, 6, "type6");
        assertEquals("groupingTransform {\n" +
                     "\tlabels {\n" +
                     "\t\t1 : label1\n" +
                     "\t\t2 : label2\n" +
                     "\t}\n" +
                     "\toffsets {\n" +
                     "\t\t3 : 3\n" +
                     "\t\t4 : 4\n" +
                     "\t}\n" +
                     "\tmaxes {\n" +
                     "\t\t5 : 5\n" +
                     "\t\t6 : 6\n" +
                     "\t}\n" +
                     "}", transform.toString());
    }

    private static GroupingTransform newTransform() {
        return new GroupingTransform(REQUEST_ID);
    }

    private static ResultId newResultId(int... indexes) {
        ResultId id = ResultId.valueOf(REQUEST_ID);
        for (int i : indexes) {
            id = id.newChildId(i);
        }
        return id;
    }

    private static OffsetContinuation newStableOffset(ResultId resultId, int tag, int offset) {
        return new OffsetContinuation(resultId, tag, offset, 0);
    }

    private static OffsetContinuation newUnstableOffset(ResultId resultId, int tag, int offset) {
        return new OffsetContinuation(resultId, tag, offset, OffsetContinuation.FLAG_UNSTABLE);
    }
}
