// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class DisjunctionTest {

    @Test
    void requireThatDisjunctionIsAnOperator() {
        assertTrue(PredicateOperator.class.isAssignableFrom(Disjunction.class));
    }

    @Test
    void requireThatAccessorsWork() {
        Disjunction node = new Disjunction();
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
        Disjunction node = new Disjunction(foo, bar);
        assertEquals(Arrays.asList(foo, bar), node.getOperands());

        node = new Disjunction(Arrays.asList(foo, bar));
        assertEquals(Arrays.asList(foo, bar), node.getOperands());
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        Disjunction node1 = new Disjunction(SimplePredicates.newString("a"), SimplePredicates.newString("b"));
        Disjunction node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
        assertNotSame(node1.getOperands(), node2.getOperands());
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(new Disjunction().hashCode(), new Disjunction().hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        Disjunction lhs = new Disjunction(SimplePredicates.newString("foo"),
                SimplePredicates.newString("bar"));
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        Disjunction rhs = new Disjunction();
        assertNotEquals(lhs, rhs);
        rhs.addOperand(SimplePredicates.newString("foo"));
        assertNotEquals(lhs, rhs);
        rhs.addOperand(SimplePredicates.newString("bar"));
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatNodeDelimiterIsOR() {
        assertEquals("", newDisjunction().toString());
        assertEquals("foo", newDisjunction("foo").toString());
        assertEquals("foo or bar", newDisjunction("foo", "bar").toString());
        assertEquals("foo or bar or baz", newDisjunction("foo", "bar", "baz").toString());
    }

    @Test
    void requireThatSimpleDisjunctionsArePrettyPrinted() {
        assertEquals("foo or bar",
                new Disjunction(SimplePredicates.newString("foo"),
                        SimplePredicates.newString("bar")).toString());
    }

    @Test
    void requireThatComplexDisjunctionsArePrintedAsGroup() {
        assertEquals("foo or bar or baz",
                new Disjunction(SimplePredicates.newString("foo"),
                        new Disjunction(SimplePredicates.newString("bar"),
                                SimplePredicates.newString("baz"))).toString());
        assertEquals("foo or (bar and baz)",
                new Disjunction(SimplePredicates.newString("foo"),
                        new Conjunction(SimplePredicates.newString("bar"),
                                SimplePredicates.newString("baz"))).toString());
    }

    private static Disjunction newDisjunction(String... operands) {
        return new Disjunction(SimplePredicates.newStrings(operands));
    }

}
