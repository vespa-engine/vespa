// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrSimplifierTest {

    @Test
    void require_that_or_with_feature_sets_of_same_key_is_simplified_to_single_feature_set() {
        Predicate p =
                or(
                        feature("key1").inSet("value1", "value4"),
                        feature("key1").inSet("value2", "value3"),
                        feature("key1").inSet("value1", "value4"));
        Predicate expected = feature("key1").inSet("value1", "value2", "value3", "value4");
        assertConvertedPredicateEquals(expected, p);
    }

    @Test
    void require_that_or_with_feature_sets_of_different_keys_is_simplified() {
        Predicate p =
                or(
                        feature("key1").inSet("value1", "value3"),
                        feature("key1").inSet("value2"),
                        feature("key2").inSet("value1"),
                        feature("key2").inSet("value2", "value3"));
        Predicate expected =
                or(
                        feature("key1").inSet("value1", "value2", "value3"),
                        feature("key2").inSet("value1", "value2", "value3"));
        assertConvertedPredicateEquals(expected, p);
    }

    @Test
    void require_that_conversion_is_recursive_and_cascades() {
        Predicate p =
                or(
                        feature("key1").inSet("value1", "value4"),
                        feature("key1").inSet("value2", "value3"),
                        or(
                                feature("key1").inSet("value1"),
                                feature("key1").inSet("value4")));
        Predicate expected = feature("key1").inSet("value1", "value2", "value3", "value4");
        assertConvertedPredicateEquals(expected, p);
    }

    @Test
    void require_that_or_below_and_is_converted() {
        Predicate p =
                and(
                        or(
                                feature("key1").inSet("value1"),
                                feature("key1").inSet("value2")),
                        feature("key2").inSet("value2"));
        Predicate expected =
                and(
                        feature("key1").inSet("value1", "value2"),
                        feature("key2").inSet("value2"));
        assertConvertedPredicateEquals(expected, p);
    }

    @Test
    void require_that_or_below_not_is_converted() {
        Predicate p =
                not(
                        or(
                                feature("key1").inSet("value1"),
                                feature("key1").inSet("value2")));
        Predicate expected = not(feature("key1").inSet("value1", "value2"));
        assertConvertedPredicateEquals(expected, p);
    }

    @Test
    void require_that_non_feature_set_nodes_are_left_untouched() {
        Predicate p =
                or(
                        feature("key1").inSet("value1"),
                        feature("key1").inSet("value2"),
                        not(feature("key1").inSet("value3")));
        Predicate expected =
                or(
                        not(feature("key1").inSet("value3")),
                        feature("key1").inSet("value1", "value2"));
        assertConvertedPredicateEquals(expected, p);
    }

    private static void assertConvertedPredicateEquals(Predicate expected, Predicate predicate) {
        OrSimplifier simplifier = new OrSimplifier();
        assertEquals(expected, simplifier.simplifyTree(predicate));
    }
}
