// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class RangeEdgePartitionTest {

    @Test
    public void requireThatRangeEdgePartitionIsAValue() {
        assertTrue(PredicateValue.class.isAssignableFrom(RangeEdgePartition.class));
    }

    @Test
    public void requireThatConstructorsWork() {
        RangeEdgePartition part = new RangeEdgePartition("foo=10", 10, 0, -1);
        assertEquals("foo=10", part.getLabel());
        assertEquals(0, part.getLowerBound());
        assertEquals(-1, part.getUpperBound());
    }

    @Test
    public void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        RangeEdgePartition node1 = new RangeEdgePartition("foo=10", 10, 0, 0);
        RangeEdgePartition node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(new RangeEdgePartition("foo=-10", 10, 2, 3).hashCode(),
                new RangeEdgePartition("foo=-10", 10, 2, 3).hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        RangeEdgePartition lhs = new RangeEdgePartition("foo=10", 10, 5, 10);
        assertTrue(lhs.equals(lhs));
        assertFalse(lhs.equals(new Object()));

        RangeEdgePartition rhs = new RangeEdgePartition("foo=20", 20, 0, 0);
        assertFalse(lhs.equals(rhs));
        rhs = new RangeEdgePartition("foo=10", 10, 5, 10);
        assertTrue(lhs.equals(rhs));
        assertFalse(lhs.equals(new RangeEdgePartition("foo=10", 10, 5, 11)));
        assertFalse(lhs.equals(new RangeEdgePartition("foo=10", 10, 6, 10)));
        assertFalse(lhs.equals(new RangeEdgePartition("foo=10", 11, 5, 10)));
        assertFalse(lhs.equals(new RangeEdgePartition("foo=11", 10, 5, 10)));
    }

    @Test
    public void requireThatKeyIsEscapedInToString() {
        assertEquals("foo=10+[2..3]", new RangeEdgePartition("foo=10", 10, 2, 3).toString());
        assertEquals("'\\foo=10'+[2..3]", new RangeEdgePartition("\foo=10", 10, 2, 3).toString());
        assertEquals("'\\x27foo\\x27=10'+[2..3]", new RangeEdgePartition("'foo'=10", 10, 2, 3).toString());
    }}
