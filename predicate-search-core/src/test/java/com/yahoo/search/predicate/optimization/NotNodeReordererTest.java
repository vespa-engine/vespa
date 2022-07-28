// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class NotNodeReordererTest {
    @Test
    void requireThatNotChildrenAreMovedAwayFromLastAndChild() {
        checkReorder(
                and(feature("a").inSet("b"), feature("c").notInSet("d")),
                and(feature("c").notInSet("d"), feature("a").inSet("b")));

        checkReorder(
                and(feature("a").inSet("b"), feature("c").notInSet("d"), feature("e").notInSet("f")),
                and(feature("c").notInSet("d"), feature("e").notInSet("f"), feature("a").inSet("b")));
    }

    @Test
    void requireThatNotChildrenAreMovedToLastOrChild() {
        checkReorder(
                or(feature("c").notInSet("d"), feature("a").inSet("b")),
                or(feature("a").inSet("b"), feature("c").notInSet("d")));

        checkReorder(
                or(feature("c").notInSet("d"), feature("e").notInSet("f"), feature("a").inSet("b")),
                or(feature("a").inSet("b"), feature("c").notInSet("d"), feature("e").notInSet("f")));
    }

    @Test
    void requireThatComplexReorderingWork() {
        checkReorder(and(feature("g").inSet("h"),
                or(and(feature("a").notInSet("b"),
                        feature("c").notInSet("d")),
                        feature("e").inSet("f"))),
                and(or(feature("e").inSet("f"),
                        and(feature("a").notInSet("b"),
                                feature("c").notInSet("d"))),
                        feature("g").inSet("h")));
    }

    private static void checkReorder(Predicate input, Predicate expected) {
        NotNodeReorderer reorderer = new NotNodeReorderer();
        Predicate actual = reorderer.process(input, new PredicateOptions(10));
        assertEquals(expected, actual);
    }
}
