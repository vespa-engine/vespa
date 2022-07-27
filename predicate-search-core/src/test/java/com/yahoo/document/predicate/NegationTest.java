// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class NegationTest {

    @Test
    void requireThatNegationIsAnOperator() {
        assertTrue(PredicateOperator.class.isAssignableFrom(Negation.class));
    }

    @Test
    void requireThatAccessorsWork() {
        Predicate foo = SimplePredicates.newString("foo");
        Negation node = new Negation(foo);
        assertSame(foo, node.getOperand());

        Predicate bar = SimplePredicates.newString("bar");
        node.setOperand(bar);
        assertSame(bar, node.getOperand());
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        Negation node1 = new Negation(SimplePredicates.newString("a"));
        Negation node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
        assertNotSame(node1.getOperand(), node2.getOperand());
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        Predicate predicate = SimplePredicates.newPredicate();
        assertEquals(new Negation(predicate).hashCode(), new Negation(predicate).hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        Negation lhs = new Negation(SimplePredicates.newString("foo"));
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        Negation rhs = new Negation(SimplePredicates.newString("bar"));
        assertNotEquals(lhs, rhs);
        rhs.setOperand(SimplePredicates.newString("foo"));
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatChildIsMandatoryInConstructor() {
        try {
            new Negation(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("operand", e.getMessage());
        }
    }

    @Test
    void requireThatChildIsMandatoryInSetter() {
        Predicate operand = SimplePredicates.newPredicate();
        Negation negation = new Negation(operand);
        try {
            negation.setOperand(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("operand", e.getMessage());
        }
        assertSame(operand, negation.getOperand());
    }

    @Test
    void requireThatChildIsIncludedInToString() {
        assertEquals("not (foo)", new Negation(SimplePredicates.newString("foo")).toString());
    }

}
