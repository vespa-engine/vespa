// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class FeatureConjunctionTransformerTest {
    private static final FeatureConjunctionTransformer transformer = new FeatureConjunctionTransformer(true);

    @Test
    void require_that_simple_ands_are_converted() {
        assertConvertedPredicateEquals(
                conj(not(featureSet(1)), featureSet(2)),
                and(not(featureSet(1)), featureSet(2))
        );
    }

    @Test
    void require_that_featureranges_are_split_into_separate_and() {
        assertConvertedPredicateEquals(
                and(featureRange(2), conj(not(featureSet(1)), featureSet(3))),
                and(not(featureSet(1)), featureRange(2), featureSet(3))
        );
    }

    @Test
    void require_that_ors_are_split_into_separate_and() {
        assertConvertedPredicateEquals(
                and(or(featureSet(1), featureSet(2)), conj(featureSet(3), featureSet(4))),
                and(or(featureSet(1), featureSet(2)), featureSet(3), featureSet(4))
        );
    }

    @Test
    void require_that_ands_must_have_more_than_one_featureset_to_be_converted() {
        assertConvertedPredicateEquals(
                and(featureSet(1), featureRange(2)),
                and(featureSet(1), featureRange(2))
        );
    }

    @Test
    void require_that_ordering_of_and_operands_are_preserved() {
        assertConvertedPredicateEquals(
                and(not(featureRange(1)), featureRange(4), conj(not(featureSet(2)), featureSet(3))),
                and(not(featureRange(1)), not(featureSet(2)), featureSet(3), featureRange(4))
        );
    }

    @Test
    void require_that_nested_ands_are_converted() {
        assertConvertedPredicateEquals(
                and(conj(featureSet(1), featureSet(2)), conj(featureSet(3), featureSet(4))),
                and(and(featureSet(1), featureSet(2)), and(featureSet(3), featureSet(4)))
        );
    }

    @Test
    void require_that_featureset_with_common_key_is_not_converted() {
        assertConvertedPredicateEquals(
                and(not(featureSet(1)), featureSet(1)),
                and(not(featureSet(1)), featureSet(1))
        );
    }

    @Test
    void require_that_nonunique_featureset_are_split_into_separate_conjunctions() {
        assertConvertedPredicateEquals(
                and(conj(not(featureSet(1)), featureSet(2)), featureSet(1)),
                and(not(featureSet(1)), featureSet(1), featureSet(2))
        );
        assertConvertedPredicateEquals(
                and(conj(not(featureSet(1)), featureSet(2)), conj(featureSet(1), featureSet(2))),
                and(not(featureSet(1)), featureSet(1), featureSet(2), featureSet(2))
        );
        assertConvertedPredicateEquals(
                and(featureRange(3), featureRange(4), conj(not(featureSet(1)), featureSet(2)), conj(featureSet(1), featureSet(2))),
                and(not(featureSet(1)), featureSet(1), featureSet(2), featureSet(2), featureRange(3), featureRange(4))
        );
    }

    @Test
    void require_that_featuresets_in_conjunction_may_only_have_a_single_value() {
        assertConvertedPredicateEquals(
                and(featureSet(1, "a", "b"), featureSet(4, "c", "d"), conj(featureSet(2), featureSet(3))),
                and(featureSet(1, "a", "b"), featureSet(2), featureSet(3), featureSet(4, "c", "d"))
        );
    }

    private static FeatureConjunction conj(Predicate... operands) {
        return new FeatureConjunction(Arrays.asList(operands));
    }

    private static FeatureSet featureSet(int id, String... values) {
        if (values.length == 0) {
            return new FeatureSet(Integer.toString(id), "a");
        }
        return new FeatureSet(Integer.toString(id), values);
    }

    private static FeatureRange featureRange(int id) {
        return new FeatureRange(Integer.toString(id));
    }

    private static void assertConvertedPredicateEquals(Predicate expectedOutput, Predicate input) {
        assertEquals(expectedOutput, transformer.process(input, new PredicateOptions(8)));
    }
}
