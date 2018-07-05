// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ConjunctionTest {

    @Test
    public void requireThatConjunctionIsAnOperator() {
        assertTrue(PredicateOperator.class.isAssignableFrom(Conjunction.class));
    }

    @Test
    public void requireThatAccessorsWork() {
        Conjunction node = new Conjunction();
        Predicate a = SimplePredicates.newString("a");
        node.addOperand(a);
        assertEquals(Arrays.asList(a), node.getOperands());
        Predicate b = SimplePredicates.newString("b");
        node.addOperand(b);
        assertEquals(Arrays.asList(a, b), node.getOperands());
        Predicate c = SimplePredicates.newString("c");
        Predicate d = SimplePredicates.newString("d");
        node.addOperands(Arrays.asList(c, d));
        assertEquals(Arrays.asList(a, b, c, d), node.getOperands());
        Predicate e = SimplePredicates.newString("e");
        Predicate f = SimplePredicates.newString("f");
        node.setOperands(Arrays.asList(e, f));
        assertEquals(Arrays.asList(e, f), node.getOperands());
    }

    @Test
    public void requireThatConstructorsWork() {
        Predicate foo = SimplePredicates.newString("foo");
        Predicate bar = SimplePredicates.newString("bar");
        Conjunction node = new Conjunction(foo, bar);
        assertEquals(Arrays.asList(foo, bar), node.getOperands());

        node = new Conjunction(Arrays.asList(foo, bar));
        assertEquals(Arrays.asList(foo, bar), node.getOperands());
    }

    @Test
    public void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        Conjunction node1 = new Conjunction(SimplePredicates.newString("a"), SimplePredicates.newString("b"));
        Conjunction node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
        assertNotSame(node1.getOperands(), node2.getOperands());
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(new Conjunction().hashCode(), new Conjunction().hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        Conjunction lhs = new Conjunction(SimplePredicates.newString("foo"),
                                          SimplePredicates.newString("bar"));
        assertTrue(lhs.equals(lhs));
        assertFalse(lhs.equals(new Object()));

        Conjunction rhs = new Conjunction();
        assertFalse(lhs.equals(rhs));
        rhs.addOperand(SimplePredicates.newString("foo"));
        assertFalse(lhs.equals(rhs));
        rhs.addOperand(SimplePredicates.newString("bar"));
        assertTrue(lhs.equals(rhs));
    }

    @Test
    public void requireThatNodeDelimiterIsAND() {
        assertEquals("", newConjunction().toString());
        assertEquals("foo", newConjunction("foo").toString());
        assertEquals("foo and bar", newConjunction("foo", "bar").toString());
        assertEquals("foo and bar and baz", newConjunction("foo", "bar", "baz").toString());
    }

    @Test
    public void requireThatSimpleConjunctionsArePrettyPrinted() {
        assertEquals("foo and bar",
                     new Conjunction(SimplePredicates.newString("foo"),
                                     SimplePredicates.newString("bar")).toString());
    }

    @Test
    public void requireThatComplexConjunctionsArePrintedAsGroup() {
        assertEquals("foo and bar and baz",
                     new Conjunction(SimplePredicates.newString("foo"),
                                     new Conjunction(SimplePredicates.newString("bar"),
                                                     SimplePredicates.newString("baz"))).toString());
        assertEquals("foo and (bar or baz)",
                     new Conjunction(SimplePredicates.newString("foo"),
                                     new Disjunction(SimplePredicates.newString("bar"),
                                                     SimplePredicates.newString("baz"))).toString());
    }

    private static Conjunction newConjunction(String... operands) {
        return new Conjunction(SimplePredicates.newStrings(operands));
    }

}
