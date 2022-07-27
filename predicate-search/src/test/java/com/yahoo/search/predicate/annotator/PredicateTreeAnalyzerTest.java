// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.annotator;

import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.PredicateOperator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.jupiter.api.Assertions.*;

public class PredicateTreeAnalyzerTest {

    @Test
    void require_that_minfeature_is_1_for_simple_term() {
        Predicate p = feature("foo").inSet("bar");
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(1, r.minFeature);
        assertEquals(1, r.treeSize);
        assertTrue(r.sizeMap.isEmpty());
    }

    @Test
    void require_that_minfeature_is_1_for_simple_negative_term() {
        Predicate p = not(feature("foo").inSet("bar"));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(1, r.minFeature);
    }

    @Test
    void require_that_minfeature_is_sum_for_and() {
        Predicate p =
                and(
                        feature("foo").inSet("bar"),
                        feature("baz").inSet("qux"),
                        feature("quux").inSet("corge"));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(3, r.minFeature);
        assertEquals(3, r.treeSize);
        assertEquals(3, r.sizeMap.size());
        assertSizeMapContains(r, pred(p).child(0), 1);
        assertSizeMapContains(r, pred(p).child(1), 1);
        assertSizeMapContains(r, pred(p).child(2), 1);
    }

    @Test
    void require_that_minfeature_is_min_for_or() {
        Predicate p =
                or(
                        and(
                                feature("foo").inSet("bar"),
                                feature("baz").inSet("qux"),
                                feature("quux").inSet("corge")),
                        and(
                                feature("grault").inSet("garply"),
                                feature("waldo").inSet("fred")));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(2, r.minFeature);
        assertEquals(5, r.treeSize);
        assertEquals(5, r.sizeMap.size());
        assertSizeMapContains(r, pred(p).child(0).child(0), 1);
        assertSizeMapContains(r, pred(p).child(0).child(1), 1);
        assertSizeMapContains(r, pred(p).child(0).child(2), 1);
        assertSizeMapContains(r, pred(p).child(1).child(0), 1);
        assertSizeMapContains(r, pred(p).child(1).child(1), 1);
    }

    @Test
    void require_that_minfeature_rounds_up() {
        Predicate p =
                or(
                        feature("foo").inSet("bar"),
                        feature("foo").inSet("bar"),
                        feature("foo").inSet("bar"));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(1, r.minFeature);
        assertEquals(3, r.treeSize);
    }

    @Test
    void require_that_minvalue_feature_set_considers_all_values() {
        {
            Predicate p =
                    and(
                            feature("foo").inSet("A", "B"),
                            feature("foo").inSet("B"));
            PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
            assertEquals(1, r.minFeature);
            assertEquals(2, r.treeSize);
        }
        {
            Predicate p =
                    and(
                            feature("foo").inSet("A", "B"),
                            feature("foo").inSet("C"));
            PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
            assertEquals(2, r.minFeature);
            assertEquals(2, r.treeSize);
        }
    }

    @Test
    void require_that_not_features_dont_count_towards_minfeature_calculation() {
        Predicate p =
                and(
                        feature("foo").inSet("A"),
                        not(feature("foo").inSet("A")),
                        not(feature("foo").inSet("B")),
                        feature("foo").inSet("B"));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(3, r.minFeature);
        assertEquals(6, r.treeSize);
    }

    @Test
    void require_that_multilevel_and_stores_size() {
        Predicate p =
                and(
                        and(
                                feature("foo").inSet("bar"),
                                feature("baz").inSet("qux"),
                                feature("quux").inSet("corge")),
                        and(
                                feature("grault").inSet("garply"),
                                feature("waldo").inSet("fred")));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(5, r.minFeature);
        assertEquals(5, r.treeSize);
        assertEquals(7, r.sizeMap.size());
        assertSizeMapContains(r, pred(p).child(0), 3);
        assertSizeMapContains(r, pred(p).child(1), 2);
        assertSizeMapContains(r, pred(p).child(0).child(0), 1);
        assertSizeMapContains(r, pred(p).child(0).child(1), 1);
        assertSizeMapContains(r, pred(p).child(0).child(2), 1);
        assertSizeMapContains(r, pred(p).child(1).child(0), 1);
        assertSizeMapContains(r, pred(p).child(1).child(1), 1);
    }

    @Test
    void require_that_not_ranges_dont_count_towards_minfeature_calculation() {
        Predicate p =
                and(
                        feature("foo").inRange(0, 10),
                        not(feature("foo").inRange(0, 10)),
                        feature("bar").inRange(0, 10),
                        not(feature("bar").inRange(0, 10)));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(3, r.minFeature);
        assertEquals(6, r.treeSize);
    }

    @Test
    void require_that_featureconjunctions_contribute_as_one_feature() {
        Predicate p =
                conj(
                        feature("foo").inSet("bar"),
                        feature("baz").inSet("qux"));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(1, r.minFeature);
        assertEquals(1, r.treeSize);
    }

    @Test
    void require_that_featureconjunctions_count_as_leaf_in_subtree_calculation() {
        Predicate p =
                and(
                        and(
                                feature("grault").inRange(0, 10),
                                feature("waldo").inRange(0, 10)),
                        conj(
                                feature("foo").inSet("bar"),
                                feature("baz").inSet("qux"),
                                feature("quux").inSet("corge")));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(3, r.minFeature);
        assertEquals(3, r.treeSize);
        assertEquals(4, r.sizeMap.size());
        assertSizeMapContains(r, pred(p).child(0), 2);
        assertSizeMapContains(r, pred(p).child(0).child(0), 1);
        assertSizeMapContains(r, pred(p).child(0).child(1), 1);
        assertSizeMapContains(r, pred(p).child(1), 1);
    }

    @Test
    void require_that_multiple_indentical_feature_conjunctions_does_not_contribute_more_than_one() {
        Predicate p =
                and(
                        or(
                                conj(
                                        feature("a").inSet("b"),
                                        feature("c").inSet("d")
                                ),
                                feature("x").inSet("y")),
                        or(
                                conj(
                                        feature("a").inSet("b"),
                                        feature("c").inSet("d")
                                ),
                                feature("z").inSet("w")));
        PredicateTreeAnalyzerResult r = PredicateTreeAnalyzer.analyzePredicateTree(p);
        assertEquals(1, r.minFeature);
        assertEquals(4, r.treeSize);
    }

    private static FeatureConjunction conj(Predicate... operands) {
        return new FeatureConjunction(Arrays.asList(operands));
    }

    private static void assertSizeMapContains(PredicateTreeAnalyzerResult r, PredicateSelector selector, int expectedValue) {
        Integer actualValue = r.sizeMap.get(selector.predicate);
        assertNotNull(actualValue);
        assertEquals(expectedValue, actualValue.intValue());
    }

    private static class PredicateSelector {
        public final Predicate predicate;

        public PredicateSelector(Predicate predicate) {
            this.predicate = predicate;
        }

        public PredicateSelector child(int index) {
            PredicateOperator op = (PredicateOperator) predicate;
            return new PredicateSelector(op.getOperands().get(index));
        }
    }

    private static PredicateSelector pred(Predicate p) {
        return new PredicateSelector(p);
    }
}
