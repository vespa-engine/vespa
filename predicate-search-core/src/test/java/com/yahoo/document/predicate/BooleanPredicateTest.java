// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class BooleanPredicateTest {

    @Test
    public void requireThatFalseIsAValue() {
        assertTrue(PredicateValue.class.isAssignableFrom(BooleanPredicate.class));
    }

    @Test
    public void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        BooleanPredicate node1 = new BooleanPredicate(true);
        BooleanPredicate node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(new BooleanPredicate(true).hashCode(), new BooleanPredicate(true).hashCode());
        assertEquals(new BooleanPredicate(false).hashCode(), new BooleanPredicate(false).hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        BooleanPredicate lhs = new BooleanPredicate(true);
        assertTrue(lhs.equals(lhs));
        assertFalse(lhs.equals(new Object()));

        BooleanPredicate rhs = new BooleanPredicate(false);
        assertFalse(lhs.equals(rhs));
        rhs.setValue(true);
        assertTrue(lhs.equals(rhs));
    }

}
