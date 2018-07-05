// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.Test;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class PredicateTest {

    @Test
    public void requireThatPredicateIsCloneable() {
        assertTrue(Cloneable.class.isAssignableFrom(Predicate.class));
    }

    @Test
    public void requireThatANDConstructsAConjunction() {
        Predicate foo = SimplePredicates.newString("foo");
        Predicate bar = SimplePredicates.newString("bar");
        Predicate predicate = and(foo, bar);
        assertEquals(Conjunction.class, predicate.getClass());
        assertEquals(new Conjunction(foo, bar), predicate);
    }

    @Test
    public void requireThatORConstructsADisjunction() {
        Predicate foo = SimplePredicates.newString("foo");
        Predicate bar = SimplePredicates.newString("bar");
        Predicate predicate = or(foo, bar);
        assertEquals(Disjunction.class, predicate.getClass());
        assertEquals(new Disjunction(foo, bar), predicate);
    }

    @Test
    public void requireThatNOTConstructsANegation() {
        Predicate foo = SimplePredicates.newString("foo");
        Predicate predicate = not(foo);
        assertEquals(Negation.class, predicate.getClass());
        assertEquals(new Negation(foo), predicate);
    }

    @Test
    public void requireThatFeatureBuilderCanConstructFeatureRange() {
        assertEquals(new FeatureRange("key", 6L, 9L),
                     feature("key").inRange(6, 9));
        assertEquals(new Negation(new FeatureRange("key", 6L, 9L)),
                     feature("key").notInRange(6, 9));
        assertEquals(new FeatureRange("key", 7L, null),
                     feature("key").greaterThan(6));
        assertEquals(new FeatureRange("key", 6L, null),
                     feature("key").greaterThanOrEqualTo(6));
        assertEquals(new FeatureRange("key", null, 5L),
                     feature("key").lessThan(6));
        assertEquals(new FeatureRange("key", null, 9L),
                     feature("key").lessThanOrEqualTo(9));
    }

    @Test
    public void requireThatFeatureBuilderCanConstructFeatureSet() {
        assertEquals(new FeatureSet("key", "valueA", "valueB"),
                     feature("key").inSet("valueA", "valueB"));
        assertEquals(new Negation(new FeatureSet("key", "valueA", "valueB")),
                     feature("key").notInSet("valueA", "valueB"));
    }

    @Test
    public void requireThatPredicatesCanBeConstructedUsingConstructors() {
        assertEquals("country in [no, se] and age in [20..30]",
                     new Conjunction(new FeatureSet("country", "no", "se"),
                                     new FeatureRange("age", 20L, 30L)).toString());
        assertEquals("country not in [no, se] or age in [20..] or height in [..160]",
                     new Disjunction(new Negation(new FeatureSet("country", "no", "se")),
                                     new FeatureRange("age", 20L, null),
                                     new FeatureRange("height", null, 160L)).toString());
    }

    @Test
    public void requireThatPredicatesCanBeBuiltUsingChainedMethodCalls() {
        assertEquals("country not in [no, se] or age in [20..] or height in [..160]",
                     new Disjunction()
                             .addOperand(new Negation(new FeatureSet("country").addValue("no").addValue("se")))
                             .addOperand(new FeatureRange("age").setFromInclusive(20L))
                             .addOperand(new FeatureRange("height").setToInclusive(160L))
                             .toString());
    }

    @Test
    public void requireThatPredicatesCanBeBuiltUsingSeparateMethodCalls() {
        Conjunction conjunction = new Conjunction();
        FeatureSet countrySet = new FeatureSet("country");
        countrySet.addValue("no");
        countrySet.addValue("se");
        conjunction.addOperand(countrySet);
        FeatureRange ageRange = new FeatureRange("age");
        ageRange.setFromInclusive(20L);
        conjunction.addOperand(ageRange);
        FeatureRange heightRange = new FeatureRange("height");
        heightRange.setToInclusive(160L);
        conjunction.addOperand(heightRange);
        assertEquals("country in [no, se] and age in [20..] and height in [..160]",
                     conjunction.toString());
    }
}
