// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static com.yahoo.document.predicate.Predicates.value;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class PredicatesTest {

    @Test
    void requireThatApiIsUsable() {
        assertEquals(
                new Disjunction(
                        new Conjunction(new FeatureSet("country", "de", "no"),
                                new Negation(new FeatureSet("gender", "female")),
                                new FeatureRange("age", 6L, 9L)),
                        new Conjunction(new Negation(new FeatureSet("country", "se")),
                                new FeatureSet("gender", "female"),
                                new FeatureRange("age", 69L, null))),
                or(and(feature("country").inSet("de", "no"),
                        feature("gender").notInSet("female"),
                        feature("age").inRange(6, 9)),
                        and(not(feature("country").inSet("se")),
                                feature("gender").inSet("female"),
                                feature("age").greaterThanOrEqualTo(69))));

        assertEquals(new BooleanPredicate(true), value(true));
        assertEquals(new BooleanPredicate(false), value(false));
    }
}
