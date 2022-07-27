// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.annotator;

import com.google.common.primitives.Ints;
import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.PredicateHash;
import com.yahoo.document.predicate.RangeEdgePartition;
import com.yahoo.document.predicate.RangePartition;
import com.yahoo.search.predicate.index.Feature;
import com.yahoo.search.predicate.index.IntervalWithBounds;
import com.yahoo.search.predicate.index.conjunction.IndexableFeatureConjunction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.yahoo.document.predicate.Predicates.and;
import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.document.predicate.Predicates.or;
import static org.junit.jupiter.api.Assertions.*;

public class PredicateTreeAnnotatorTest {

    @Test
    void require_that_or_intervals_are_the_same() {
        Predicate p =
                or(
                        feature("key1").inSet("value1"),
                        feature("key2").inSet("value2"));
        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(1, r.minFeature);
        assertEquals(2, r.intervalEnd);
        assertEquals(2, r.intervalMap.size());
        assertIntervalContains(r, "key1=value1", 0x00010002);
        assertIntervalContains(r, "key2=value2", 0x00010002);
    }

    @Test
    void require_that_ands_below_ors_get_different_intervals() {
        Predicate p =
                or(
                        and(
                                feature("key1").inSet("value1"),
                                feature("key1").inSet("value1"),
                                feature("key1").inSet("value1")),
                        and(
                                feature("key2").inSet("value2"),
                                feature("key2").inSet("value2"),
                                feature("key2").inSet("value2")));
        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(1, r.minFeature);
        assertEquals(6, r.intervalEnd);
        assertEquals(2, r.intervalMap.size());
        assertIntervalContains(r, "key1=value1", 0x00010001, 0x00020002, 0x00030006);
        assertIntervalContains(r, "key2=value2", 0x00010004, 0x00050005, 0x00060006);
    }

    @Test
    void require_that_nots_get_correct_intervals() {
        Predicate p =
                and(
                        feature("key").inSet("value"),
                        not(feature("key").inSet("value")),
                        feature("key").inSet("value"),
                        not(feature("key").inSet("value")));
        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(2, r.minFeature);
        assertEquals(6, r.intervalEnd);
        assertEquals(2, r.intervalMap.size());
        assertIntervalContains(r, "key=value", 0x00010001, 0x00020002, 0x00040004, 0x00050005);
        assertIntervalContains(r, Feature.Z_STAR_COMPRESSED_ATTRIBUTE_NAME, 0x00020001, 0x00050004);
    }

    @Test
    void require_that_final_first_not_interval_is_extended() {
        Predicate p = not(feature("key").inSet("A"));
        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(1, r.minFeature);
        assertEquals(2, r.intervalEnd);
        assertEquals(2, r.intervalMap.size());
        assertIntervalContains(r, "key=A", 0x00010001);
        assertIntervalContains(r, Feature.Z_STAR_COMPRESSED_ATTRIBUTE_NAME, 0x00010000);
    }

    @Test
    void show_different_types_of_not_intervals() {
        {
            Predicate p =
                    and(
                            or(
                                    and(
                                            feature("key").inSet("A"),
                                            not(feature("key").inSet("B"))),
                                    and(
                                            not(feature("key").inSet("C")),
                                            feature("key").inSet("D"))),
                            feature("foo").inSet("bar"));
            PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
            assertEquals(3, r.minFeature);
            assertEquals(7, r.intervalEnd);
            assertEquals(6, r.intervalMap.size());

            assertIntervalContains(r, "foo=bar", 0x00070007);
            assertIntervalContains(r, "key=A", 0x00010001);
            assertIntervalContains(r, "key=B", 0x00020002);
            assertIntervalContains(r, "key=C", 0x00010004);
            assertIntervalContains(r, "key=D", 0x00060006);
            assertIntervalContains(r, Feature.Z_STAR_COMPRESSED_ATTRIBUTE_NAME, 0x00020001, 0x00000006, 0x00040000);
        }
        {
            Predicate p =
                    or(
                            not(feature("key").inSet("A")),
                            not(feature("key").inSet("B")));

            PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
            assertEquals(1, r.minFeature);
            assertEquals(4, r.intervalEnd);
            assertEquals(3, r.intervalMap.size());
            assertIntervalContains(r, "key=A", 0x00010003);
            assertIntervalContains(r, "key=B", 0x00010003);
            assertIntervalContains(r, Feature.Z_STAR_COMPRESSED_ATTRIBUTE_NAME, 0x00030000, 0x00030000);
        }
        {
            Predicate p =
                    or(
                            and(
                                    not(feature("key").inSet("A")),
                                    not(feature("key").inSet("B"))),
                            and(
                                    not(feature("key").inSet("C")),
                                    not(feature("key").inSet("D"))));

            PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
            assertEquals(1, r.minFeature);
            assertEquals(8, r.intervalEnd);
            assertEquals(5, r.intervalMap.size());
            assertIntervalContains(r, "key=A", 0x00010001);
            assertIntervalContains(r, "key=B", 0x00030007);
            assertIntervalContains(r, "key=C", 0x00010005);
            assertIntervalContains(r, "key=D", 0x00070007);
            assertIntervalContains(r, Feature.Z_STAR_COMPRESSED_ATTRIBUTE_NAME,
                    0x00010000, 0x00070002, 0x00050000, 0x00070006);
        }
    }

    @Test
    void require_that_hashed_ranges_get_correct_intervals() {
        Predicate p =
                and(
                        range("key",
                                partition("key=10-19"),
                                partition("key=20-29"),
                                edgePartition("key=0", 5, 10, 20),
                                edgePartition("key=30", 0, 0, 3)),
                        range("foo",
                                partition("foo=10-19"),
                                partition("foo=20-29"),
                                edgePartition("foo=0", 5, 40, 60),
                                edgePartition("foo=30", 0, 0, 3)));


        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(2, r.minFeature);
        assertEquals(2, r.intervalEnd);
        assertEquals(4, r.intervalMap.size());
        assertEquals(4, r.boundsMap.size());
        assertIntervalContains(r, "key=10-19", 0x00010001);
        assertIntervalContains(r, "key=20-29", 0x00010001);
        assertBoundsContains(r, "key=0", bound(0x00010001, 0x000a0015));  // [10..20]
        assertBoundsContains(r, "key=30", bound(0x00010001, 0x40000004));  // [..3]

        assertIntervalContains(r, "foo=10-19", 0x00020002);
        assertIntervalContains(r, "foo=20-29", 0x00020002);
        assertBoundsContains(r, "foo=0", bound(0x00020002, 0x0028003d));  // [40..60]
        assertBoundsContains(r, "foo=30", bound(0x00020002, 0x40000004));  // [..3]
    }

    @Test
    void require_that_extreme_ranges_works() {
        Predicate p =
                and(
                        range("max range", partition("max range=9223372036854775806-9223372036854775807")),
                        range("max edge", edgePartition("max edge=9223372036854775807", 0, 0, 1)),
                        range("min range", partition("min range=-9223372036854775807-9223372036854775806")),
                        range("min edge", edgePartition("min edge=-9223372036854775808", 0, 0, 1)));
        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(4, r.minFeature);
        assertEquals(4, r.intervalEnd);
        assertEquals(2, r.intervalMap.size());
        assertEquals(2, r.boundsMap.size());
        assertIntervalContains(r, "max range=9223372036854775806-9223372036854775807", 0x00010001);
        assertBoundsContains(r, "max edge=9223372036854775807", bound(0x00020002, 0x40000002));
        assertIntervalContains(r, "min range=-9223372036854775807-9223372036854775806", 0x00030003);
        assertBoundsContains(r, "min edge=-9223372036854775808", bound(0x00040004, 0x40000002));
    }

    @Test
    void require_that_featureconjunctions_are_registered_and_given_an_interval() {
        Predicate p =
                and(
                        or(
                                range("key",
                                        partition("key=10-19"),
                                        partition("key=20-29"),
                                        edgePartition("key=0", 5, 10, 20),
                                        edgePartition("key=30", 0, 0, 3)),
                                conj(
                                        not(feature("keyA").inSet("C")),
                                        feature("keyB").inSet("D"))),
                        feature("foo").inSet("bar"));
        PredicateTreeAnnotations r = PredicateTreeAnnotator.createPredicateTreeAnnotations(p);
        assertEquals(2, r.minFeature);
        assertEquals(3, r.intervalEnd);
        assertEquals(3, r.intervalMap.size());
        assertEquals(2, r.boundsMap.size());
        assertEquals(1, r.featureConjunctions.size());

        Map.Entry<IndexableFeatureConjunction, List<Integer>> entry = r.featureConjunctions.entrySet().iterator().next();
        assertEquals(1, entry.getValue().size());
        assertEquals(0b1_0000000000000010, entry.getValue().get(0).longValue());
    }

    private static void assertIntervalContains(PredicateTreeAnnotations r, String feature, Integer... expectedIntervals) {
        long hash = PredicateHash.hash64(feature);
        List<Integer> actualIntervals = r.intervalMap.get(hash);
        assertNotNull(actualIntervals);
        assertArrayEquals(Ints.toArray(Arrays.asList(expectedIntervals)), Ints.toArray(actualIntervals));
    }

    private static void assertBoundsContains(PredicateTreeAnnotations r, String feature, IntervalWithBounds expectedBounds) {
        long hash = PredicateHash.hash64(feature);
        List<IntervalWithBounds> actualBounds = r.boundsMap.get(hash);
        assertNotNull(actualBounds);
        assertEquals(1, actualBounds.size());
        assertEquals(expectedBounds, actualBounds.get(0));
    }

    private static IntervalWithBounds bound(int interval, int bounds) {
        return new IntervalWithBounds(interval, bounds);
    }

    private static RangePartition partition(String label) {
        return new RangePartition(label);
    }

    private static RangePartition edgePartition(String label, long value, int lower, int upper) {
        return new RangeEdgePartition(label, value, lower, upper);
    }

    private static FeatureRange range(String key, RangePartition... partitions) {
        return range(key, null, null, partitions);
    }

    private static FeatureRange range(String key, Long lower, Long upper, RangePartition... partitions) {
        FeatureRange range = new FeatureRange(key, lower, upper);
        Arrays.asList(partitions).forEach(range::addPartition);
        return range;
    }

    private static FeatureConjunction conj(Predicate... operands) {
        return new FeatureConjunction(Arrays.asList(operands));
    }
}
