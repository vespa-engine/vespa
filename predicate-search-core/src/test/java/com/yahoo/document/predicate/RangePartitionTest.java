// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class RangePartitionTest {

    @Test
    void requireThatRangePartitionIsAValue() {
        assertTrue(PredicateValue.class.isAssignableFrom(RangePartition.class));
    }

    @Test
    void requireThatConstructorsWork() {
        RangePartition part = new RangePartition("foo=10-19");
        assertEquals("foo=10-19", part.getLabel());
        part = new RangePartition("foo", 10, 19, false);
        assertEquals("foo=10-19", part.getLabel());
        part = new RangePartition("foo", 10, 19, true);
        assertEquals("foo=-19-10", part.getLabel());
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        RangePartition node1 = new RangePartition("foo=300-399");
        RangePartition node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(new RangePartition("foo=0-9").hashCode(), new RangePartition("foo=0-9").hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        RangePartition lhs = new RangePartition("foo=10-19");
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        RangePartition rhs = new RangePartition("bar=1000-1999");
        assertNotEquals(lhs, rhs);
        rhs = new RangePartition("foo=10-19");
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatKeyIsEscapedInToString() {
        assertEquals("foo=10-19", new RangePartition("foo=10-19").toString());
        assertEquals("'\\foo=10-19'", new RangePartition("\foo=10-19").toString());
        assertEquals("'\\x27foo\\x27=10-19'", new RangePartition("'foo'=10-19").toString());
    }
}
