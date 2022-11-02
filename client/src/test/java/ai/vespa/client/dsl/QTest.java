// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author unknown contributor
 * @author bjorncs
 */
class QTest {

    @Test
    void select_specific_fields() {
        String q = Q.select("f1", "f2")
                .from("sd1")
                .where("f1").contains("v1")
                .build();

        assertEquals(q, "yql=select f1, f2 from sd1 where f1 contains \"v1\"");
    }

    @Test
    void select_from_specific_sources() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").contains("v1")
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 contains \"v1\"");
    }

    @Test
    void select_from_multiples_sources() {
        String q = Q.select("*")
                .from("sd1", "sd2")
                .where("f1").contains("v1")
                .build();

        assertEquals(q, "yql=select * from sources sd1, sd2 where f1 contains \"v1\"");
    }

    @Test
    void basic_and_andnot_or_offset_limit_param_order_by_and_contains() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").contains("v1")
                .and("f2").contains("v2")
                .or("f3").contains("v3")
                .andnot("f4").contains("v4")
                .offset(1)
                .limit(2)
                .timeout(3)
                .orderByDesc("f1")
                .orderByAsc("f2")
                .fix()
                .param("paramk1", "paramv1")
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 contains \"v1\" and f2 contains \"v2\" or f3 contains \"v3\" and !(f4 contains \"v4\") order by f1 desc, f2 asc limit 2 offset 1 timeout 3&paramk1=paramv1");
    }

    @Test
    void matches() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").matches("v1")
                .and("f2").matches("v2")
                .or("f3").matches("v3")
                .andnot("f4").matches("v4")
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 matches \"v1\" and f2 matches \"v2\" or f3 matches \"v3\" and !(f4 matches \"v4\")");
    }

    @Test
    void numeric_operations() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").le(1)
                .and("f2").lt(2)
                .and("f3").ge(3)
                .and("f4").gt(4)
                .and("f5").eq(5)
                .and("f6").inRange(6, 7)
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 <= 1 and f2 < 2 and f3 >= 3 and f4 > 4 and f5 = 5 and range(f6, 6, 7)");
    }

    @Test
    void long_numeric_operations() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").le(1L)
                .and("f2").lt(2L)
                .and("f3").ge(3L)
                .and("f4").gt(4L)
                .and("f5").eq(5L)
                .and("f6").inRange(6L, 7L)
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 <= 1L and f2 < 2L and f3 >= 3L and f4 > 4L and f5 = 5L and range(f6, 6L, 7L)");
    }

    @Test
    void float_numeric_operations() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").le(1.1)
                .and("f2").lt(2.2)
                .and("f3").ge(3.3)
                .and("f4").gt(4.4)
                .and("f5").eq(5.5)
                .and("f6").inRange(6.6, 7.7)
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 <= 1.1 and f2 < 2.2 and f3 >= 3.3 and f4 > 4.4 and f5 = 5.5 and range(f6, 6.6, 7.7)");
    }

    @Test
    void double_numeric_operations() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").le(1.1D)
                .and("f2").lt(2.2D)
                .and("f3").ge(3.3D)
                .and("f4").gt(4.4D)
                .and("f5").eq(5.5D)
                .and("f6").inRange(6.6D, 7.7D)
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 <= 1.1 and f2 < 2.2 and f3 >= 3.3 and f4 > 4.4 and f5 = 5.5 and range(f6, 6.6, 7.7)");
    }

    @Test
    void nested_queries() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").contains("1")
                .andnot(Q.p(Q.p("f2").contains("2").and("f3").contains("3"))
                        .or(Q.p("f2").contains("4").andnot("f3").contains("5")))
                .build();

        assertEquals(q, "yql=select * from sd1 where f1 contains \"1\" and !((f2 contains \"2\" and f3 contains \"3\") or (f2 contains \"4\" and !(f3 contains \"5\")))");
    }

    @Test
    void userInput_with_and_without_defaultIndex() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.ui("value1"))
                .and(Q.ui("index", "value2"))
                .build();
        assertEquals("yql=select * from sd1 where userInput(\"value1\") and ({\"defaultIndex\":\"index\"}userInput(\"value2\"))", q);
    }

    @Test
    void userInput_with_rank() {
        String q = Q.select("url")
                    .from("site")
                    .where(Q.rank(Q.p("docQ").nearestNeighbor("vectorQuery"),
                                  Q.ui("@query")))
                    .build();
        assertEquals("yql=select url from site where rank(nearestNeighbor(docQ, vectorQuery), userInput(@query))", q);
    }

    @Test
    void dot_product() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.dotPdt("f1", stringIntMap("a", 1, "b", 2, "c", 3)))
            .and("f2").contains("1")
                .build();

        assertEquals("yql=select * from sd1 where dotProduct(f1, {\"a\":1,\"b\":2,\"c\":3}) and f2 contains \"1\"", q);
    }

    @Test
    void weighted_set() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.wtdSet("f1", stringIntMap("a", 1, "b", 2, "c", 3)))
                .and("f2").contains("1")
                .build();

        assertEquals(q, "yql=select * from sd1 where weightedSet(f1, {\"a\":1,\"b\":2,\"c\":3}) and f2 contains \"1\"");
    }

    @Test
    void non_empty() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.nonEmpty(Q.p("f1").contains("v1")))
                .and("f2").contains("v2")
                .build();

        assertEquals(q, "yql=select * from sd1 where nonEmpty(f1 contains \"v1\") and f2 contains \"v2\"");
    }


    @Test
    void wand_with_and_without_annotation() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.wand("f1", stringIntMap("a", 1, "b", 2, "c", 3)))
                .and(Q.wand("f2", Arrays.asList(Arrays.asList(1, 1), Arrays.asList(2, 2))))
                .and(
                        Q.wand("f3", Arrays.asList(Arrays.asList(1, 1), Arrays.asList(2, 2)))
                                .annotate(A.a("scoreThreshold", 0.13))
                )
                .build();

        assertEquals(q, "yql=select * from sd1 where wand(f1, {\"a\":1,\"b\":2,\"c\":3}) and wand(f2, [[1,1],[2,2]]) and ({\"scoreThreshold\":0.13}wand(f3, [[1,1],[2,2]]))");
    }

    @Test
    void weak_and_with_and_without_annotation() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.weakand(Q.p("f1").contains("v1").and("f2").contains("v2")))
                .and(Q.weakand(Q.p("f1").contains("v1").and("f2").contains("v2"))
                        .annotate(A.a("scoreThreshold", 0.13))
                )
                .build();

        assertEquals(q, "yql=select * from sd1 where weakAnd(f1 contains \"v1\", f2 contains \"v2\") and ({\"scoreThreshold\":0.13}weakAnd(f1 contains \"v1\", f2 contains \"v2\"))");
    }

    @Test
    void geo_location() {
        String q = Q.select("*")
                .from("sd1")
                .where("a").contains("b").and(Q.geoLocation("taiwan", 25.105497, 121.597366, "200km"))
                .build();

        assertEquals(q, "yql=select * from sd1 where a contains \"b\" and geoLocation(taiwan, 25.105497, 121.597366, \"200km\")");
    }

    @Test
    void nearest_neighbor_query() {
        String q = Q.select("*")
                .from("sd1")
                .where("a").contains("b")
                .and(Q.nearestNeighbor("vec1", "vec2")
                        .annotate(A.a("targetHits", 10, "approximate", false))
                )
                .build();
        assertEquals(q, "yql=select * from sd1 where a contains \"b\" and ([{\"approximate\":false,\"targetHits\":10}]nearestNeighbor(vec1, vec2))");
    }

    @Test
    void invalid_nearest_neighbor_should_throws_an_exception_targetHits_annotation_is_required() {
        assertThrows(IllegalArgumentException.class,
                () -> Q.select("*")
                        .from("sd1")
                        .where("a").contains("b").and(Q.nearestNeighbor("vec1", "vec2"))
                        .build());
    }


    @Test
    void rank_with_only_query() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.rank(
                        Q.p("f1").contains("v1")
                        )
                )
                .build();

        assertEquals(q, "yql=select * from sd1 where rank(f1 contains \"v1\")");
    }

    @Test
    void rank() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.rank(
                        Q.p("f1").contains("v1"),
                        Q.p("f2").contains("v2"),
                        Q.p("f3").eq(3))
                )
                .build();

        assertEquals(q, "yql=select * from sd1 where rank(f1 contains \"v1\", f2 contains \"v2\", f3 = 3)");
    }

    @Test
    void rank_with_rank_query_array() {
        Query[] ranks = new Query[]{Q.p("f2").contains("v2"), Q.p("f3").eq(3)};
        String q = Q.select("*")
                .from("sd1")
                .where(Q.rank(
                        Q.p("f1").contains("v1"),
                        ranks)
                )
                .build();

        assertEquals(q, "yql=select * from sd1 where rank(f1 contains \"v1\", f2 contains \"v2\", f3 = 3)");
    }

    @Test
    void stringfunction_annotations() {
        {
            Annotation annotation = A.filter();
            String expected = "[{\"filter\":true}]";
            String q = Q.select("*")
                    .from("sd1")
                    .where("f1").contains(annotation, "v1")
                    .build();

            assertEquals(q, "yql=select * from sd1 where f1 contains (" + expected + "\"v1\")");
        }
        {
            Annotation annotation = A.defaultIndex("idx");
            String expected = "[{\"defaultIndex\":\"idx\"}]";
            String q = Q.select("*")
                    .from("sd1")
                    .where("f1").contains(annotation, "v1")
                    .build();

            assertEquals(q, "yql=select * from sd1 where f1 contains (" + expected + "\"v1\")");
        }
        {
            Annotation annotation = A.a(stringObjMap("a1", stringObjMap("k1", "v1", "k2", 2)));
            String expected = "[{\"a1\":{\"k1\":\"v1\",\"k2\":2}}]";
            String q = Q.select("*")
                    .from("sd1")
                    .where("f1").contains(annotation, "v1")
                    .build();

            assertEquals(q, "yql=select * from sd1 where f1 contains (" + expected + "\"v1\")");
        }

    }

    @Test
    void sub_expression_annotations() {
        String q = Q.select("*")
                .from("sd1")
                .where("f1").contains("v1").annotate(A.a("ak1", "av1"))
                .build();

        assertEquals(q, "yql=select * from sd1 where ([{\"ak1\":\"av1\"}](f1 contains \"v1\"))");
    }

    @Test
    void sub_expressions_annotations_annotate_in_the_middle_of_query() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.p("f1").contains("v1").annotate(A.a("ak1", "av1")).and("f2").contains("v2"))
                .build();

        assertEquals(q, "yql=select * from sd1 where ([{\"ak1\":\"av1\"}](f1 contains \"v1\" and f2 contains \"v2\"))");
    }

    @Test
    void sub_expressions_annotations_annotate_in_nested_queries() {
        String q = Q.select("*")
                .from("sd1")
                .where(Q.p(
                        Q.p("f1").contains("v1").annotate(A.a("ak1", "av1")))
                        .and("f2").contains("v2")
                )
                .build();

        assertEquals(q, "yql=select * from sd1 where (([{\"ak1\":\"av1\"}](f1 contains \"v1\")) and f2 contains \"v2\")");
    }

    @Test
    void build_query_which_created_from_Q_b_without_select_and_sources() {
        String q = Q.p("f1").contains("v1")
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains \"v1\"");
    }

    @Test
    void order_by() {
        String q = Q.p("f1").contains("v1")
                .orderByAsc("f2")
                .orderByAsc(A.a(stringObjMap("function", "uca", "locale", "en_US", "strength", "IDENTICAL")), "f3")
            .orderByDesc("f4")
                .orderByDesc(A.a(stringObjMap("function", "lowercase")), "f5")
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains \"v1\" order by f2 asc, {\"function\":\"uca\",\"locale\":\"en_US\",\"strength\":\"IDENTICAL\"}f3 asc, f4 desc, {\"function\":\"lowercase\"}f5 desc");
    }

    @Test
    void contains_sameElement() {
        String q = Q.p("f1").containsSameElement(Q.p("stime").le(1).and("etime").gt(2))
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains sameElement(stime <= 1, etime > 2)");
    }

    @Test
    void contains_phrase_near_onear_equiv() {
        {
            String q1 = Q.p("f1").containsPhrase("p1", "p2", "p3")
                    .build();
            String q2 = Q.p("f1").containsPhrase(Arrays.asList("p1", "p2", "p3"))
                    .build();
            assertEquals(q1, "yql=select * from sources * where f1 contains phrase(\"p1\", \"p2\", \"p3\")");
            assertEquals(q2, "yql=select * from sources * where f1 contains phrase(\"p1\", \"p2\", \"p3\")");
        }
        {
            String q1 = Q.p("f1").containsNear("p1", "p2", "p3")
                    .build();
            String q2 = Q.p("f1").containsNear(Arrays.asList("p1", "p2", "p3"))
                    .build();
            assertEquals(q1, "yql=select * from sources * where f1 contains near(\"p1\", \"p2\", \"p3\")");
            assertEquals(q2, "yql=select * from sources * where f1 contains near(\"p1\", \"p2\", \"p3\")");
        }
        {
            String q1 = Q.p("f1").containsOnear("p1", "p2", "p3")
                    .build();
            String q2 = Q.p("f1").containsOnear(Arrays.asList("p1", "p2", "p3"))
                    .build();
            assertEquals(q1, "yql=select * from sources * where f1 contains onear(\"p1\", \"p2\", \"p3\")");
            assertEquals(q2, "yql=select * from sources * where f1 contains onear(\"p1\", \"p2\", \"p3\")");
        }
        {
            String q1 = Q.p("f1").containsEquiv("p1", "p2", "p3")
                    .build();
            String q2 = Q.p("f1").containsEquiv(Arrays.asList("p1", "p2", "p3"))
                    .build();
            assertEquals(q1, "yql=select * from sources * where f1 contains equiv(\"p1\", \"p2\", \"p3\")");
            assertEquals(q2, "yql=select * from sources * where f1 contains equiv(\"p1\", \"p2\", \"p3\")");
        }
    }

    @Test
    void contains_uri() {
        String q = Q.p("f1").containsUri("https://test.uri")
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains uri(\"https://test.uri\")");
    }

    @Test
    void contains_uri_with_annotation() {
        String q = Q.p("f1").containsUri(A.a("key", "value"), "https://test.uri")
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains ([{\"key\":\"value\"}]uri(\"https://test.uri\"))");
    }

    @Test
    void nearestNeighbor() {
        String q = Q.p("f1").nearestNeighbor("query_vector")
                .build();

        assertEquals(q, "yql=select * from sources * where nearestNeighbor(f1, query_vector)");
    }

    @Test
    void nearestNeighbor_with_annotation() {
        String q = Q.p("f1").nearestNeighbor(A.a("targetHits", 10), "query_vector")
                .build();

        assertEquals(q, "yql=select * from sources * where ([{\"targetHits\":10}]nearestNeighbor(f1, query_vector))");
    }

    @Test
    void use_contains_instead_of_contains_equiv_when_input_size_is_1() {
        String q = Q.p("f1").containsEquiv(Collections.singletonList("p1"))
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains \"p1\"");
    }

    @Test
    void contains_phrase_near_onear_equiv_empty_list_should_throw_illegal_argument_exception() {
        assertThrows(IllegalArgumentException.class, () -> Q.p("f1").containsPhrase(Collections.emptyList())
                .build());

        assertThrows(IllegalArgumentException.class, () -> Q.p("f1").containsNear(Collections.emptyList())
                .build());

        assertThrows(IllegalArgumentException.class, () -> Q.p("f1").containsOnear(Collections.emptyList())
                .build());

        assertThrows(IllegalArgumentException.class, () -> Q.p("f1").containsEquiv(Collections.emptyList())
                .build());
    }


    @Test
    void contains_near_onear_with_annotation() {
        {
            String q = Q.p("f1").containsNear(A.a("distance", 5), "p1", "p2", "p3")
                    .build();

            assertEquals(q, "yql=select * from sources * where f1 contains ([{\"distance\":5}]near(\"p1\", \"p2\", \"p3\"))");
        }
        {
            String q = Q.p("f1").containsOnear(A.a("distance", 5), "p1", "p2", "p3")
                    .build();

            assertEquals(q, "yql=select * from sources * where f1 contains ([{\"distance\":5}]onear(\"p1\", \"p2\", \"p3\"))");
        }
    }

    @Test
    void basic_group_syntax() {
        /*
        example from vespa document:
        https://docs.vespa.ai/en/grouping.html
        all( group(a) max(5) each(output(count())
            all(max(1) each(output(summary())))
            all(group(b) each(output(count())
                all(max(1) each(output(summary())))
                all(group(c) each(output(count())
                    all(max(1) each(output(summary())))))))) );
         */
        String q = Q.p("f1").contains("v1")
                .group(
                        G.all(G.group("a"), G.maxRtn(5), G.each(G.output(G.count()),
                                G.all(G.maxRtn(1), G.each(G.output(G.summary()))),
                                G.all(G.group("b"), G.each(G.output(G.count()),
                                        G.all(G.maxRtn(1), G.each(G.output(G.summary()))),
                                        G.all(G.group("c"), G.each(G.output(G.count()),
                                                G.all(G.maxRtn(1), G.each(G.output(G.summary())))
                                        ))
                                ))
                        ))
                )
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains \"v1\" | all(group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))))");
    }

    @Test
    void set_group_syntax_string_directly() {
        /*
        example from vespa document:
        https://docs.vespa.ai/en/grouping.html
        all( group(a) max(5) each(output(count())
            all(max(1) each(output(summary())))
            all(group(b) each(output(count())
                all(max(1) each(output(summary())))
                all(group(c) each(output(count())
                    all(max(1) each(output(summary())))))))) );
         */
        String q = Q.p("f1").contains("v1")
                .group("all(group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))))")
                .build();

        assertEquals(q, "yql=select * from sources * where f1 contains \"v1\" | all(group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))))");
    }

@Test
    void arbitrary_annotations() {
        Annotation a = A.a("a1", "v1", "a2", 2, "a3", stringObjMap("k", "v", "k2", 1), "a4", 4D, "a5", Arrays.asList(1, 2, 3));
        assertEquals(a.toString(), "{\"a1\":\"v1\",\"a2\":2,\"a3\":{\"k\":\"v\",\"k2\":1},\"a4\":4.0,\"a5\":[1,2,3]}");
    }

    @Test
    void test_programmability() {
        Map<String, String> map = stringStringMap("a", "1", "b", "2", "c", "3");

        Query q = map
                .entrySet()
                .stream()
                .map(entry -> Q.p(entry.getKey()).contains(entry.getValue()))
                .reduce(Query::and)
                .get();

        assertEquals(q.build(), "yql=select * from sources * where a contains \"1\" and b contains \"2\" and c contains \"3\"");
    }

    @Test
    void test_programmability_2() {
        Map<String, String> map = stringStringMap("a", "1", "b", "2", "c", "3");
        Query q = Q.p();

        map.forEach((k, v) -> q.and(Q.p(k).contains(v)));

        assertEquals(q.build(), "yql=select * from sources * where a contains \"1\" and b contains \"2\" and c contains \"3\"");
    }

    @Test
    void empty_queries_should_not_print_out() {
        String q = Q.p(Q.p(Q.p().andnot(Q.p()).and(Q.p()))).and("a").contains("1").build();

        assertEquals(q, "yql=select * from sources * where a contains \"1\"");
    }

    @Test
    void validate_positive_search_term_of_strings() {
        Query q = Q.p(Q.p("k1").contains("v1").and("k2").contains("v2").andnot("k3").contains("v3"))
                .andnot(Q.p("nk1").contains("nv1").and("nk2").contains("nv2").andnot("nk3").contains("nv3"))
                .and(Q.p("k4").contains("v4")
                        .andnot(Q.p("k5").contains("v5").andnot("k6").contains("v6"))
                );

        assertTrue(q.hasPositiveSearchField("k1"));
        assertTrue(q.hasPositiveSearchField("k2"));
        assertTrue(q.hasPositiveSearchField("nk3"));
        assertTrue(q.hasPositiveSearchField("k6"));
        assertTrue(q.hasPositiveSearchField("k6", "v6"));
        assertFalse(q.hasPositiveSearchField("k6", "v5"));

        assertTrue(q.hasNegativeSearchField("k3"));
        assertTrue(q.hasNegativeSearchField("nk1"));
        assertTrue(q.hasNegativeSearchField("nk2"));
        assertTrue(q.hasNegativeSearchField("k5"));
        assertTrue(q.hasNegativeSearchField("k5", "v5"));
        assertFalse(q.hasNegativeSearchField("k5", "v4"));
    }

    @Test
    void validate_positive_search_term_of_user_input() {
        Query q = Q.p(Q.ui("k1", "v1")).and(Q.ui("k2", "v2")).andnot(Q.ui("k3", "v3"))
                .andnot(Q.p(Q.ui("nk1", "nv1")).and(Q.ui("nk2", "nv2")).andnot(Q.ui("nk3", "nv3")))
                .and(Q.p(Q.ui("k4", "v4"))
                        .andnot(Q.p(Q.ui("k5", "v5")).andnot(Q.ui("k6", "v6")))
                );

        assertTrue(q.hasPositiveSearchField("k1"));
        assertTrue(q.hasPositiveSearchField("k2"));
        assertTrue(q.hasPositiveSearchField("nk3"));
        assertTrue(q.hasPositiveSearchField("k6"));
        assertTrue(q.hasPositiveSearchField("k6", "v6"));
        assertFalse(q.hasPositiveSearchField("k6", "v5"));

        assertTrue(q.hasNegativeSearchField("k3"));
        assertTrue(q.hasNegativeSearchField("nk1"));
        assertTrue(q.hasNegativeSearchField("nk2"));
        assertTrue(q.hasNegativeSearchField("k5"));
        assertTrue(q.hasNegativeSearchField("k5", "v5"));
        assertFalse(q.hasNegativeSearchField("k5", "v4"));
    }

    private static Map<String, Integer> stringIntMap(String k1, int v1, String k2, int v2, String k3, int v3) {
        HashMap<String, Integer> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    private static Map<String, Object> stringObjMap(String k, Object v) {
        HashMap<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    private static Map<String, Object> stringObjMap(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static Map<String, Object> stringObjMap(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    private static Map<String, String> stringStringMap(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

}