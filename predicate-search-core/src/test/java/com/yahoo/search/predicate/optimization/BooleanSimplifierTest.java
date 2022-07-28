// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import static com.yahoo.document.predicate.Predicates.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class BooleanSimplifierTest {

    @Test
    void requireThatOrOfTrueIsTrue() {
        assertSimplifiedTo(value(true), or(feature("a").inSet("b"), value(true)));
    }

    @Test
    void requireThatFalseChildrenOfOrAreRemoved() {
        assertSimplifiedTo(feature("a").inSet("b"), or(feature("a").inSet("b"), value(false)));
    }

    @Test
    void requireThatAndOfFalseIsFalse() {
        assertSimplifiedTo(value(false), and(feature("a").inSet("b"), value(false)));
    }

    @Test
    void requireThatTrueChildrenOfAndAreRemoved() {
        assertSimplifiedTo(feature("a").inSet("b"), and(feature("a").inSet("b"), value(true)));
    }

    @Test
    void requireThatSingleChildAndOrAreRemoved() {
        assertSimplifiedTo(feature("a").inSet("b"), and(or(and(feature("a").inSet("b")))));
    }

    @Test
    void requireThatValueChildrenOfNotAreInverted() {
        assertSimplifiedTo(value(true), not(value(false)));
        assertSimplifiedTo(value(false), not(value(true)));
        assertSimplifiedTo(value(true), not(not(not(value(false)))));
        assertSimplifiedTo(value(true), not(not(not(and(feature("a").inSet("b"), value(false))))));
    }

    @Test
    void requireThatComplexExpressionIsSimplified() {
        assertSimplifiedTo(
                Predicate.fromString("'pub_entity' not in [301951]"),
                Predicate.fromString("true and true and true and true and true and 'pub_entity' not in [301951] and ((true and true and true and true) or (true and true and true and true) or (true and true and true and true and 'pub_entity' in [86271]))"));
    }

    private void assertSimplifiedTo(Predicate expected, Predicate input) {
        BooleanSimplifier simplifier = new BooleanSimplifier();
        Predicate actual = simplifier.process(input, new PredicateOptions(10));
        assertEquals(expected, actual);
    }
}
