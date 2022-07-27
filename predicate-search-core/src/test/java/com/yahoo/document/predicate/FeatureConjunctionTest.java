// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
public class FeatureConjunctionTest {

    @Test
    void require_that_featureconjunction_with_valid_operands_can_be_constructed() {
        new FeatureConjunction(Arrays.asList(
                not(feature("a").inSet("1")),
                feature("b").inSet("1")));
    }

    @Test
    void require_that_constructor_throws_exception_if_all_operands_are_not_featuresets() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FeatureConjunction(Arrays.asList(
                    not(feature("a").inSet("1")),
                    feature("b").inRange(1, 2)));
        });
    }

    @Test
    void require_that_constructor_throws_exception_if_single_operand() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FeatureConjunction(Arrays.asList(feature("a").inSet("1")));
        });
    }

    @Test
    void require_that_constructor_throws_exception_if_no_operands() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FeatureConjunction(Collections.emptyList());
        });
    }

    @Test
    void require_that_contructor_throws_exception_if_featuresets_contain_multiple_values() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FeatureConjunction(Arrays.asList(feature("a").inSet("1"), feature("b").inSet("2", "3")));
        });
    }

    @Test
    void require_that_constructor_throws_exception_if_featureset_keys_are_not_unique() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FeatureConjunction(Arrays.asList(
                    not(feature("a").inSet("1")),
                    feature("a").inSet("2")));
        });
    }
}
