// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

import com.yahoo.document.predicate.FeatureConjunction;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.search.predicate.PredicateQuery;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.yahoo.document.predicate.Predicates.feature;
import static com.yahoo.document.predicate.Predicates.not;
import static com.yahoo.search.predicate.serialization.SerializationTestHelper.assertSerializationDeserializationMatches;
import static org.junit.jupiter.api.Assertions.*;

public class ConjunctionIndexTest {

    @Test
    void require_that_single_conjunction_can_be_indexed() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        builder.indexConjunction(indexableConj(conj(feature("a").inSet("1"), feature("b").inSet("2"))));
        assertEquals(2, builder.calculateFeatureCount());
        assertEquals(1, builder.getUniqueConjunctionCount());
    }

    @Test
    void require_that_large_conjunction_can_be_indexed() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        builder.indexConjunction(indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("1"),
                        feature("c").inSet("1"))));
        assertEquals(3, builder.calculateFeatureCount());
        assertEquals(1, builder.getUniqueConjunctionCount());
    }

    @Test
    void require_that_multiple_conjunctions_can_be_indexed() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        builder.indexConjunction(indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("3"))));
        builder.indexConjunction(indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("3")))); // Duplicate
        builder.indexConjunction(indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("2"),
                        feature("c").inSet("3"))));
        builder.indexConjunction(indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("2"),
                        feature("c").inSet("3")))); // Duplicate
        builder.indexConjunction(indexableConj(
                conj(
                        feature("d").inSet("1"),
                        feature("e").inSet("5"))));
        assertEquals(6, builder.calculateFeatureCount());
        assertEquals(3, builder.getUniqueConjunctionCount());
    }

    @Test
    void require_that_search_for_simple_conjunctions_work() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();

        IndexableFeatureConjunction c1 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("2")));
        IndexableFeatureConjunction c2 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("2"),
                        feature("c").inSet("3")));
        IndexableFeatureConjunction c3 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("5")));

        builder.indexConjunction(c1);
        builder.indexConjunction(c2);
        builder.indexConjunction(c3);
        ConjunctionIndex index = builder.build();
        ConjunctionIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();
        query.addFeature("a", "1");
        query.addFeature("b", "2");
        assertHitsEquals(searcher.search(query), c1);
        query.addFeature("c", "3");
        assertHitsEquals(searcher.search(query), c1, c2);
        query.addFeature("b", "5");
        assertHitsEquals(searcher.search(query), c1, c2, c3);
    }


    @Test
    void require_that_conjunction_with_not_is_indexed() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        builder.indexConjunction(indexableConj(
                conj(
                        not(feature("a").inSet("1")),
                        not(feature("b").inSet("1")))));
        builder.indexConjunction(indexableConj(
                conj(
                        feature("a").inSet("1"),
                        not(feature("b").inSet("1")))));
        assertEquals(2, builder.calculateFeatureCount());
        assertEquals(2, builder.getUniqueConjunctionCount());
        assertEquals(1, builder.getZListSize());
    }

    @Test
    void require_that_not_works_when_k_is_0() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        IndexableFeatureConjunction c1 = indexableConj(
                conj(
                        not(feature("a").inSet("1")),
                        not(feature("b").inSet("1"))));
        IndexableFeatureConjunction c2 = indexableConj(
                conj(
                        not(feature("a").inSet("1")),
                        not(feature("b").inSet("1")),
                        not(feature("c").inSet("1"))));
        IndexableFeatureConjunction c3 = indexableConj(
                conj(
                        not(feature("a").inSet("1")),
                        not(feature("b").inSet("1")),
                        not(feature("c").inSet("1")),
                        not(feature("d").inSet("1"))));
        IndexableFeatureConjunction c4 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("1")));
        builder.indexConjunction(c1);
        builder.indexConjunction(c2);
        builder.indexConjunction(c3);
        builder.indexConjunction(c4);
        ConjunctionIndex index = builder.build();
        ConjunctionIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();
        assertHitsEquals(searcher.search(query), c1, c2, c3);
        query.addFeature("a", "1");
        query.addFeature("b", "1");
        assertHitsEquals(searcher.search(query), c4);
        query.addFeature("c", "1");
        assertHitsEquals(searcher.search(query), c4);
        query.addFeature("d", "1");
        assertHitsEquals(searcher.search(query), c4);
    }

    @Test
    void require_that_not_works_when_k_is_1() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        IndexableFeatureConjunction c1 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        not(feature("b").inSet("1"))));
        IndexableFeatureConjunction c2 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        not(feature("b").inSet("1")),
                        not(feature("c").inSet("1"))));
        IndexableFeatureConjunction c3 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        not(feature("b").inSet("1")),
                        not(feature("c").inSet("1")),
                        not(feature("d").inSet("1"))));
        builder.indexConjunction(c1);
        builder.indexConjunction(c2);
        builder.indexConjunction(c3);
        ConjunctionIndex index = builder.build();
        ConjunctionIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();
        assertTrue(searcher.search(query).isEmpty());
        query.addFeature("a", "1");
        assertHitsEquals(searcher.search(query), c1, c2, c3);
        query.addFeature("b", "1");
        assertTrue(searcher.search(query).isEmpty());
        query.addFeature("c", "1");
        assertTrue(searcher.search(query).isEmpty());
        query.addFeature("d", "1");
        assertTrue(searcher.search(query).isEmpty());
    }

    @Test
    void require_that_not_works_when_k_is_2() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        IndexableFeatureConjunction c1 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("1"),
                        not(feature("c").inSet("1"))));
        IndexableFeatureConjunction c2 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("1"),
                        not(feature("c").inSet("1")),
                        not(feature("d").inSet("1"))));
        IndexableFeatureConjunction c3 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("1"),
                        not(feature("c").inSet("1")),
                        not(feature("d").inSet("1")),
                        not(feature("e").inSet("1"))));
        builder.indexConjunction(c1);
        builder.indexConjunction(c2);
        builder.indexConjunction(c3);
        ConjunctionIndex index = builder.build();
        ConjunctionIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();
        query.addFeature("a", "1");
        query.addFeature("b", "1");
        assertHitsEquals(searcher.search(query), c1, c2, c3);
        query.addFeature("c", "1");
        assertTrue(searcher.search(query).isEmpty());
        query.addFeature("d", "1");
        assertTrue(searcher.search(query).isEmpty());
        query.addFeature("e", "1");
        assertTrue(searcher.search(query).isEmpty());
    }

    @Test
    void require_that_multi_term_queries_are_supported() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        IndexableFeatureConjunction c1 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("3")));
        builder.indexConjunction(c1);
        ConjunctionIndex index = builder.build();
        ConjunctionIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();
        query.addFeature("a", "1");
        query.addFeature("a", "2");
        assertTrue(searcher.search(query).isEmpty());
        query.addFeature("b", "3");
        assertHitsEquals(searcher.search(query), c1);
    }

    @Test
    void require_that_subqueries_are_supported() {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        IndexableFeatureConjunction c1 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("3"),
                        not(feature("c").inSet("4"))));
        IndexableFeatureConjunction c2 = indexableConj(
                conj(
                        feature("a").inSet("1"),
                        feature("b").inSet("3")));
        IndexableFeatureConjunction c3 = indexableConj(
                conj(
                        feature("a").inSet("2"),
                        feature("b").inSet("3")));
        IndexableFeatureConjunction c4 = indexableConj(
                conj(
                        feature("e").inSet("5"),
                        feature("f").inSet("6"))
        );
        builder.indexConjunction(c1);
        builder.indexConjunction(c2);
        builder.indexConjunction(c3);
        builder.indexConjunction(c4);
        ConjunctionIndex index = builder.build();
        ConjunctionIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();

        //subquery 0: a=2 and b=3
        //subquery 1: a=1 and b=3
        //subquery 2: a=1 and b=3
        query.addFeature("a", "1", 0b110);
        query.addFeature("a", "2", 0b001);
        query.addFeature("b", "3", 0b111);
        List<ConjunctionHit> expectedHits = matchingConjunctionList(
                new ConjunctionHit(c1.id, 0b110),
                new ConjunctionHit(c2.id, 0b110),
                new ConjunctionHit(c3.id, 0b001)
        );

        List<ConjunctionHit> hits = searcher.search(query);
        assertHitsEquals(expectedHits, hits);

        //subquery 0: a=2 and b=3 and c=4
        //subquery 1: a=1 and b=3
        //subquery 2: a=1 and b=3 and c=4
        query.addFeature("c", "4", 0b101);
        expectedHits = matchingConjunctionList(
                new ConjunctionHit(c1.id, 0b010),
                new ConjunctionHit(c2.id, 0b110),
                new ConjunctionHit(c3.id, 0b001)
        );
        hits = searcher.search(query);
        assertHitsEquals(expectedHits, hits);

        // subquery 0: a=2 and e=5
        // subquery 1: b=3 and f=6
        PredicateQuery query2 = new PredicateQuery();
        query2.addFeature("a", "2", 0b01);
        query2.addFeature("b", "3", 0b10);
        query2.addFeature("e", "5", 0b01);
        query2.addFeature("f", "6", 0b10);
        expectedHits = matchingConjunctionList(
                new ConjunctionHit(c1.id, 0b010),
                new ConjunctionHit(c2.id, 0b110),
                new ConjunctionHit(c3.id, 0b001)
        );
        hits = searcher.search(query);
        assertHitsEquals(expectedHits, hits);
    }

    @Test
    void require_that_serialization_and_deserialization_retain_data() throws IOException {
        ConjunctionIndexBuilder builder = new ConjunctionIndexBuilder();
        builder.indexConjunction(indexableConj(
                conj(
                        not(feature("a").inSet("1")),
                        not(feature("b").inSet("3")),
                        not(feature("c").inSet("4")))));
        builder.indexConjunction(indexableConj(
                conj(
                        feature("d").inSet("5"),
                        feature("e").inSet("6"))));
        ConjunctionIndex index = builder.build();
        assertSerializationDeserializationMatches(
                index, ConjunctionIndex::writeToOutputStream, ConjunctionIndex::fromInputStream);
    }

    private static List<ConjunctionHit> matchingConjunctionList(ConjunctionHit... conjunctionHits) {
        return Arrays.asList(conjunctionHits);
    }

    private static void assertHitsEquals(List<ConjunctionHit> hits, IndexableFeatureConjunction... conjunctions) {
        Arrays.sort(conjunctions, (c1, c2) -> Long.compareUnsigned(c1.id, c2.id));
        Collections.sort(hits);
        assertEquals(conjunctions.length, hits.size());
        for (int i = 0; i < hits.size(); i++) {
            assertEquals(conjunctions[i].id, hits.get(i).conjunctionId);
        }
    }

    private static void assertHitsEquals(List<ConjunctionHit> expectedHits, List<ConjunctionHit> hits) {
        Collections.sort(expectedHits);
        Collections.sort(hits);
        assertArrayEquals(
                expectedHits.toArray(new ConjunctionHit[expectedHits.size()]),
                hits.toArray(new ConjunctionHit[expectedHits.size()]));
    }

    private static FeatureConjunction conj(Predicate... operands) {
        return new FeatureConjunction(Arrays.asList(operands));
    }

    private static IndexableFeatureConjunction indexableConj(FeatureConjunction conjunction) {
        return new IndexableFeatureConjunction(conjunction);
    }
}
