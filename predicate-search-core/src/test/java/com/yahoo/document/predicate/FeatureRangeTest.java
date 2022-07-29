// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class FeatureRangeTest {

    @Test
    void requireThatFeatureRangeIsAValue() {
        assertTrue(PredicateValue.class.isAssignableFrom(FeatureRange.class));
    }

    @Test
    void requireThatAccessorsWork() {
        FeatureRange node = new FeatureRange("foo");
        assertEquals("foo", node.getKey());
        node.setKey("bar");
        assertEquals("bar", node.getKey());

        node.setFromInclusive(69L);
        assertEquals(69, node.getFromInclusive().intValue());
        node.setFromInclusive(null);
        assertNull(node.getFromInclusive());

        node.setToInclusive(69L);
        assertEquals(69, node.getToInclusive().intValue());
        node.setToInclusive(null);
        assertNull(node.getToInclusive());
    }

    @Test
    void requireThatConstructorsWork() {
        FeatureRange node = new FeatureRange("foo");
        assertEquals("foo", node.getKey());
        assertNull(node.getFromInclusive());
        assertNull(node.getToInclusive());

        node = new FeatureRange("foo", null, null);
        assertEquals("foo", node.getKey());
        assertNull(node.getFromInclusive());
        assertNull(node.getToInclusive());

        node = new FeatureRange("foo", 69L, null);
        assertEquals("foo", node.getKey());
        assertEquals(69, node.getFromInclusive().intValue());
        assertNull(node.getToInclusive());

        node = new FeatureRange("foo", null, 69L);
        assertEquals("foo", node.getKey());
        assertNull(node.getFromInclusive());
        assertEquals(69, node.getToInclusive().intValue());

        node = new FeatureRange("foo", 6L, 9L);
        assertEquals("foo", node.getKey());
        assertEquals(6, node.getFromInclusive().intValue());
        assertEquals(9, node.getToInclusive().intValue());
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        FeatureRange node1 = new FeatureRange("foo", 6L, 9L);
        FeatureRange node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(new FeatureRange("key").hashCode(), new FeatureRange("key").hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        FeatureRange lhs = new FeatureRange("foo", 6L, 9L);
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        FeatureRange rhs = new FeatureRange("bar");
        assertNotEquals(lhs, rhs);
        rhs.setKey("foo");
        assertNotEquals(lhs, rhs);
        rhs.setFromInclusive(6L);
        assertNotEquals(lhs, rhs);
        rhs.setToInclusive(9L);
        assertEquals(lhs, rhs);
        rhs.addPartition(new RangePartition("foo"));
        assertNotEquals(lhs, rhs);
        lhs.addPartition(new RangePartition("foo"));
        assertEquals(lhs, rhs);
        rhs.addPartition(new RangeEdgePartition("foo", 10, 0, 2));
        assertNotEquals(lhs, rhs);
        lhs.addPartition(new RangeEdgePartition("foo", 10, 0, 2));
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatFeatureKeyIsMandatoryInConstructor() {
        try {
            new FeatureRange(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("key", e.getMessage());
        }
    }

    @Test
    void requireThatFeatureKeyIsMandatoryInSetter() {
        FeatureRange node = new FeatureRange("foo");
        try {
            node.setKey(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("key", e.getMessage());
        }
        assertEquals("foo", node.getKey());
    }

    @Test
    void requireThatRangeCanBeSingleValue() {
        FeatureRange node = new FeatureRange("key", 6L, 6L);
        assertEquals(6, node.getFromInclusive().intValue());
        assertEquals(6, node.getToInclusive().intValue());
        node.setToInclusive(9L);
        node.setFromInclusive(9L);
        assertEquals(9, node.getFromInclusive().intValue());
        assertEquals(9, node.getToInclusive().intValue());
    }

    @Test
    void requireThatFromCanNotBeConstructedGreaterThanTo() {
        try {
            new FeatureRange("key", 9L, 6L);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected 'to' greater than or equal to 9, got 6.", e.getMessage());
        }
    }

    @Test
    void requireThatFromCanNotBeSetGreaterThanTo() {
        FeatureRange node = new FeatureRange("key", null, 6L);
        try {
            node.setFromInclusive(9L);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected 'from' less than or equal to 6, got 9.", e.getMessage());
        }
        assertNull(node.getFromInclusive());

        node = new FeatureRange("key", 6L, 9L);
        try {
            node.setFromInclusive(69L);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected 'from' less than or equal to 9, got 69.", e.getMessage());
        }
        assertEquals(6, node.getFromInclusive().intValue());
    }

    @Test
    void requireThatToCanNotBeSetLessThanFrom() {
        FeatureRange node = new FeatureRange("key", 9L, null);
        try {
            node.setToInclusive(6L);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected 'to' greater than or equal to 9, got 6.", e.getMessage());
        }
        assertNull(node.getToInclusive());

        node = new FeatureRange("key", 6L, 9L);
        try {
            node.setToInclusive(1L);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected 'to' greater than or equal to 6, got 1.", e.getMessage());
        }
        assertEquals(9, node.getToInclusive().intValue());
    }

    @Test
    void requireThatKeyIsEscapedInToString() {
        assertEquals("foo in [6..9]",
                new FeatureRange("foo", 6L, 9L).toString());
        assertEquals("'\\foo' in [6..9]",
                new FeatureRange("\foo", 6L, 9L).toString());
        assertEquals("'\\x27foo\\x27' in [6..9]",
                new FeatureRange("'foo'", 6L, 9L).toString());
    }

    @Test
    void requireThatToStringIncludesLimits() {
        assertEquals("foo in [6..9]", new FeatureRange("foo", 6L, 9L).toString());
    }

    @Test
    void requireThatToStringAllowsNullLimits() {
        assertEquals("foo in [..]", new FeatureRange("foo").toString());
    }

    @Test
    void requireThatToStringAllowsNullFromLimit() {
        assertEquals("foo in [..69]", new FeatureRange("foo", null, 69L).toString());
    }

    @Test
    void requireThatToStringAllowsNullToLimit() {
        assertEquals("foo in [69..]", new FeatureRange("foo", 69L, null).toString());
    }

    @Test
    void requireThatSimpleStringsArePrettyPrinted() {
        assertEquals("foo in [6..9]",
                new FeatureRange("foo", 6L, 9L).toString());
    }

    @Test
    void requireThatComplexStringsAreEscaped() {
        assertEquals("'\\foo' in [6..9]",
                new FeatureRange("\foo", 6L, 9L).toString());
    }

    @Test
    void requireThatRangePartitionsCanBeAdded() {
        FeatureRange range = new FeatureRange("foo", 10L, 22L);
        range.addPartition(new RangePartition("foo=10-19"));
        range.addPartition(new RangePartition("foo", 0, 0x8000000000000000L, true));
        range.addPartition(new RangeEdgePartition("foo=20", 20, 0, 2));
        assertEquals("foo in [10..22 (foo=20+[..2],foo=10-19,foo=-9223372036854775808-0)]", range.toString());
    }

    @Test
    void requireThatRangePartitionsCanBeCleared() {
        FeatureRange range = new FeatureRange("foo", 10L, 22L);
        range.addPartition(new RangePartition("foo=10-19"));
        range.addPartition(new RangeEdgePartition("foo=20", 20, 0, 2));
        assertEquals("foo in [10..22 (foo=20+[..2],foo=10-19)]", range.toString());
        range.clearPartitions();
        assertEquals("foo in [10..22]", range.toString());
    }

    @Test
    void requireThatFeatureRangeCanBeBuiltFromMixedInNode() {
        assertEquals(new FeatureRange("foo", 10L, 19L),
                FeatureRange.buildFromMixedIn("foo", Arrays.asList("foo=10-19"), 10));
        assertEquals(new FeatureRange("foo", -19L, -10L),
                FeatureRange.buildFromMixedIn("foo", Arrays.asList("foo=-10-19"), 10));
        assertEquals(new FeatureRange("foo", 10L, 19L),
                FeatureRange.buildFromMixedIn("foo", Arrays.asList("foo=10,10,9"), 10));
        assertEquals(new FeatureRange("foo", 10L, 19L),
                FeatureRange.buildFromMixedIn("foo", Arrays.asList("foo=10,10,1073741833"), 10));
        assertEquals(new FeatureRange("foo", 10L, 19L),
                FeatureRange.buildFromMixedIn("foo", Arrays.asList("foo=10,10,2147483648"), 10));
    }

}
