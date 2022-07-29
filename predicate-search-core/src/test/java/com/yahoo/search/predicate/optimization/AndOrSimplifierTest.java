// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class AndOrSimplifierTest {

    @Test
    void requireThatNestedConjunctionsAreCollapsed() {
        assertSimplified(and(feature("a").inSet("b"),
                feature("c").inSet("d")),
                and(and(feature("a").inSet("b"),
                        feature("c").inSet("d"))));
    }

    @Test
    void requireThatNestedConjuctionsAreCollapsedInPlace() {
        assertSimplified(and(feature("a").inSet("b"),
                feature("c").inSet("d"),
                feature("e").inSet("f")),
                and(feature("a").inSet("b"),
                        and(feature("c").inSet("d")),
                        feature("e").inSet("f")));
    }

    @Test
    void requireThatDeeplyNestedConjunctionsAreCollapsed() {
        assertSimplified(and(feature("a").inSet("b"),
                feature("c").inSet("d"),
                feature("e").inSet("f"),
                feature("g").inSet("h"),
                feature("i").inSet("j")),
                and(feature("a").inSet("b"),
                        and(feature("c").inSet("d")),
                        feature("e").inSet("f"),
                        and(and(feature("g").inSet("h"),
                                feature("i").inSet("j")))));
    }

    @Test
    void requireThatNestedDisjunctionsAreCollapsed() {
        assertSimplified(or(feature("a").inSet("b"),
                feature("c").inSet("d")),
                or(or(feature("a").inSet("b"),
                        feature("c").inSet("d"))));
    }

    @Test
    void requireThatNestedDisjuctionsAreCollapsedInPlace() {
        assertSimplified(or(feature("a").inSet("b"),
                feature("c").inSet("d"),
                feature("e").inSet("f")),
                or(feature("a").inSet("b"),
                        or(feature("c").inSet("d")),
                        feature("e").inSet("f")));
    }

    @Test
    void requireThatDeeplyNestedDisjunctionsAreCollapsed() {
        assertSimplified(or(feature("a").inSet("b"),
                feature("c").inSet("d"),
                feature("e").inSet("f"),
                feature("g").inSet("h"),
                feature("i").inSet("j")),
                or(feature("a").inSet("b"),
                        or(feature("c").inSet("d")),
                        feature("e").inSet("f"),
                        or(or(feature("g").inSet("h"),
                                feature("i").inSet("j")))));
    }

    @Test
    void requireThatConjunctionsAndDisjunctionsAreNotCollapsed() {
        assertSimplified(and(or(feature("a").inSet("b"),
                feature("c").inSet("d"))),
                and(or(feature("a").inSet("b"),
                        feature("c").inSet("d"))));
    }

    @Test
    void requireThatNotOrIsTranslatedToAndNot() {
        assertSimplified(
                and(feature("a").notInSet("b"), feature("c").inSet("d")),
                not(or(feature("a").inSet("b"), feature("c").notInSet("d"))));
    }

    @Test
    void requireThatNotAndIsTranslatedToOrNot() {
        assertSimplified(
                or(feature("a").notInSet("b"), feature("c").inSet("d")),
                not(and(feature("a").inSet("b"), feature("c").notInSet("d"))));
    }

    @Test
    void requireThatTreeWithoutNotIsNotAffected() {
        assertSimplified(
                and(feature("a").inSet("b"), feature("c").notInSet("d")),
                and(feature("a").inSet("b"), feature("c").notInSet("d")));
        assertSimplified(
                or(feature("a").inSet("b"), feature("c").notInSet("d")),
                or(feature("a").inSet("b"), feature("c").notInSet("d")));
        assertSimplified(feature("a").inSet("b"), feature("a").inSet("b"));
    }

    @Test
    void requireThatNotOfNotIsRemoved() {
        assertSimplified(feature("a").inSet("b"), not(not(feature("a").inSet("b"))));
        assertSimplified(feature("a").inSet("b"), not(feature("a").notInSet("b")));
        assertSimplified(feature("a").notInSet("b"), not(not(feature("a").notInSet("b"))));
    }

    @Test
    void requireThatNotBeneathAndIsTranslated() {
        assertSimplified(
                and(feature("a").notInSet("b"), feature("c").inSet("d"), feature("b").inSet("c")),
                and(not(or(feature("a").inSet("b"), feature("c").notInSet("d"))), feature("b").inSet("c")));
    }

    private static void assertSimplified(Predicate expected, Predicate input) {
        AndOrSimplifier simplifier = new AndOrSimplifier();
        Predicate actual = simplifier.process(input, new PredicateOptions(10));
        assertEquals(expected, actual);
    }
}
