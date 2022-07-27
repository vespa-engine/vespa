// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ConjunctionTest {

    @Test
    void requireThatConjunctionIsAnOperator() {
        assertTrue(PredicateOperator.class.isAssignableFrom(Conjunction.class));
    }

    @Test
    void requireThatAccessorsWork() {
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
    void requireThatConstructorsWork() {
        Predicate foo = SimplePredicates.newString("foo");
        Predicate bar = SimplePredicates.newString("bar");
        Conjunction node = new Conjunction(foo, bar);
        assertEquals(Arrays.asList(foo, bar), node.getOperands());

        node = new Conjunction(Arrays.asList(foo, bar));
        assertEquals(Arrays.asList(foo, bar), node.getOperands());
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        Conjunction node1 = new Conjunction(SimplePredicates.newString("a"), SimplePredicates.newString("b"));
        Conjunction node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
        assertNotSame(node1.getOperands(), node2.getOperands());
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(new Conjunction().hashCode(), new Conjunction().hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        Conjunction lhs = new Conjunction(SimplePredicates.newString("foo"),
                SimplePredicates.newString("bar"));
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        Conjunction rhs = new Conjunction();
        assertNotEquals(lhs, rhs);
        rhs.addOperand(SimplePredicates.newString("foo"));
        assertNotEquals(lhs, rhs);
        rhs.addOperand(SimplePredicates.newString("bar"));
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatNodeDelimiterIsAND() {
        assertEquals("", newConjunction().toString());
        assertEquals("foo", newConjunction("foo").toString());
        assertEquals("foo and bar", newConjunction("foo", "bar").toString());
        assertEquals("foo and bar and baz", newConjunction("foo", "bar", "baz").toString());
    }

    @Test
    void requireThatSimpleConjunctionsArePrettyPrinted() {
        assertEquals("foo and bar",
                new Conjunction(SimplePredicates.newString("foo"),
                        SimplePredicates.newString("bar")).toString());
    }

    @Test
    void requireThatComplexConjunctionsArePrintedAsGroup() {
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
