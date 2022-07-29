// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class BooleanPredicateTest {

    @Test
    void requireThatFalseIsAValue() {
        assertTrue(PredicateValue.class.isAssignableFrom(BooleanPredicate.class));
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        BooleanPredicate node1 = new BooleanPredicate(true);
        BooleanPredicate node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(new BooleanPredicate(true).hashCode(), new BooleanPredicate(true).hashCode());
        assertEquals(new BooleanPredicate(false).hashCode(), new BooleanPredicate(false).hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        BooleanPredicate lhs = new BooleanPredicate(true);
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        BooleanPredicate rhs = new BooleanPredicate(false);
        assertNotEquals(lhs, rhs);
        rhs.setValue(true);
        assertEquals(lhs, rhs);
    }

}
