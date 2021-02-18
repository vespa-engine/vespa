// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl

import spock.lang.Specification

class QTest extends Specification {

    def "select specific fields"() {
        given:
        def q = Q.select("f1", "f2")
            .from("sd1")
            .where("f1").contains("v1")
            .semicolon()
            .build()

        expect:
        q == """yql=select f1, f2 from sd1 where f1 contains "v1";"""
    }

    def "select from specific sources"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").contains("v1")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where f1 contains "v1";"""
    }

    def "select from multiples sources"() {
        given:
        def q = Q.select("*")
            .from("sd1", "sd2")
            .where("f1").contains("v1")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources sd1, sd2 where f1 contains "v1";"""
    }

    def "basic 'and', 'andnot', 'or', 'offset', 'limit', 'param', 'order by', and 'contains'"() {
        given:
        def q = Q.select("*")
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
            .semicolon()
            .param("paramk1", "paramv1")
            .build()

        expect:
        q == """yql=select * from sd1 where f1 contains "v1" and f2 contains "v2" or f3 contains "v3" and !(f4 contains "v4") order by f1 desc, f2 asc limit 2 offset 1 timeout 3;&paramk1=paramv1"""
    }

    def "matches"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").matches("v1")
            .and("f2").matches("v2")
            .or("f3").matches("v3")
            .andnot("f4").matches("v4")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where f1 matches "v1" and f2 matches "v2" or f3 matches "v3" and !(f4 matches "v4");"""
    }

    def "numeric operations"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").le(1)
            .and("f2").lt(2)
            .and("f3").ge(3)
            .and("f4").gt(4)
            .and("f5").eq(5)
            .and("f6").inRange(6, 7)
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where f1 <= 1 and f2 < 2 and f3 >= 3 and f4 > 4 and f5 = 5 and range(f6, 6, 7);"""
    }

    def "long numeric operations"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").le(1L)
            .and("f2").lt(2L)
            .and("f3").ge(3L)
            .and("f4").gt(4L)
            .and("f5").eq(5L)
            .and("f6").inRange(6L, 7L)
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where f1 <= 1L and f2 < 2L and f3 >= 3L and f4 > 4L and f5 = 5L and range(f6, 6L, 7L);"""
    }

    def "float numeric operations"() {
        given:
        def q = Q.select("*")
                .from("sd1")
                .where("f1").le(1.1)
                .and("f2").lt(2.2)
                .and("f3").ge(3.3)
                .and("f4").gt(4.4)
                .and("f5").eq(5.5)
                .and("f6").inRange(6.6, 7.7)
                .semicolon()
                .build()

        expect:
        q == """yql=select * from sd1 where f1 <= 1.1 and f2 < 2.2 and f3 >= 3.3 and f4 > 4.4 and f5 = 5.5 and range(f6, 6.6, 7.7);"""
    }

    def "double numeric operations"() {
        given:
        def q = Q.select("*")
                .from("sd1")
                .where("f1").le(1.1D)
                .and("f2").lt(2.2D)
                .and("f3").ge(3.3D)
                .and("f4").gt(4.4D)
                .and("f5").eq(5.5D)
                .and("f6").inRange(6.6D, 7.7D)
                .semicolon()
                .build()

        expect:
        q == """yql=select * from sd1 where f1 <= 1.1 and f2 < 2.2 and f3 >= 3.3 and f4 > 4.4 and f5 = 5.5 and range(f6, 6.6, 7.7);"""
    }

    def "nested queries"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").contains("1")
            .andnot(Q.p(Q.p("f2").contains("2").and("f3").contains("3"))
                .or(Q.p("f2").contains("4").andnot("f3").contains("5")))
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where f1 contains "1" and !((f2 contains "2" and f3 contains "3") or (f2 contains "4" and !(f3 contains "5")));"""
    }

    def "userInput (with and with out defaultIndex)"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.ui("value"))
            .and(Q.ui("index", "value2"))
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where userInput(@_1) and ([{"defaultIndex":"index"}]userInput(@_2_index));&_2_index=value2&_1=value"""
    }

    def "dot product"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.dotPdt("f1", [a: 1, b: 2, c: 3]))
            .and("f2").contains("1")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where dotProduct(f1, {"a":1,"b":2,"c":3}) and f2 contains "1";"""
    }

    def "weighted set"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.wtdSet("f1", [a: 1, b: 2, c: 3]))
            .and("f2").contains("1")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where weightedSet(f1, {"a":1,"b":2,"c":3}) and f2 contains "1";"""
    }

    def "non empty"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.nonEmpty(Q.p("f1").contains("v1")))
            .and("f2").contains("v2")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where nonEmpty(f1 contains "v1") and f2 contains "v2";"""
    }


    def "wand (with and without annotation)"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.wand("f1", [a: 1, b: 2, c: 3]))
            .and(Q.wand("f2", [[1, 1], [2, 2]]))
            .and(
                Q.wand("f3", [[1, 1], [2, 2]])
                    .annotate(A.a("scoreThreshold", 0.13))
            )
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where wand(f1, {"a":1,"b":2,"c":3}) and wand(f2, [[1,1],[2,2]]) and ([{"scoreThreshold":0.13}]wand(f3, [[1,1],[2,2]]));"""
    }

    def "weak and (with and without annotation)"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.weakand("f1", Q.p("f1").contains("v1").and("f2").contains("v2")))
            .and(Q.weakand("f3", Q.p("f1").contains("v1").and("f2").contains("v2"))
                .annotate(A.a("scoreThreshold", 0.13))
            )
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where weakAnd(f1, f1 contains "v1", f2 contains "v2") and ([{"scoreThreshold":0.13}]weakAnd(f3, f1 contains "v1", f2 contains "v2"));"""
    }

    def "rank with only query"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.rank(
                Q.p("f1").contains("v1")
            )
            )
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where rank(f1 contains "v1");"""
    }

    def "rank"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.rank(
                Q.p("f1").contains("v1"),
                Q.p("f2").contains("v2"),
                Q.p("f3").eq(3))
            )
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where rank(f1 contains "v1", f2 contains "v2", f3 = 3);"""
    }

    def "rank with rank query array"() {
        given:
        Query[] ranks = [Q.p("f2").contains("v2"), Q.p("f3").eq(3)].toArray()
        def q = Q.select("*")
            .from("sd1")
            .where(Q.rank(
                Q.p("f1").contains("v1"),
                ranks)
            )
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where rank(f1 contains "v1", f2 contains "v2", f3 = 3);"""
    }

    def "string/function annotations"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").contains(annotation, "v1")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where f1 contains (${expected}"v1");"""

        where:
        annotation                   | expected
        A.filter()                   | """[{"filter":true}]"""
        A.defaultIndex("idx")        | """[{"defaultIndex":"idx"}]"""
        A.a([a1: [k1: "v1", k2: 2]]) | """[{"a1":{"k1":"v1","k2":2}}]"""
    }

    def "sub-expression annotations"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where("f1").contains("v1").annotate(A.a("ak1", "av1"))
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where ([{"ak1":"av1"}](f1 contains "v1"));"""
    }

    def "sub-expressions annotations (annotate in the middle of query)"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.p("f1").contains("v1").annotate(A.a("ak1", "av1")).and("f2").contains("v2"))
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where ([{"ak1":"av1"}](f1 contains "v1" and f2 contains "v2"));"""
    }

    def "sub-expressions annotations (annotate in nested queries)"() {
        given:
        def q = Q.select("*")
            .from("sd1")
            .where(Q.p(
                Q.p("f1").contains("v1").annotate(A.a("ak1", "av1")))
                .and("f2").contains("v2")
            )
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sd1 where (([{"ak1":"av1"}](f1 contains "v1")) and f2 contains "v2");"""
    }

    def "build query which created from Q.b without select and sources"() {
        given:
        def q = Q.p("f1").contains("v1")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains "v1";"""
    }

    def "order by"() {
        given:
        def q = Q.p("f1").contains("v1")
            .orderByAsc("f2")
            .orderByAsc(A.a([function: "uca", locale: "en_US", strength: "IDENTICAL"]), "f3")
            .orderByDesc("f4")
            .orderByDesc(A.a([function: "lowercase"]), "f5")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains "v1" order by f2 asc, [{"function":"uca","locale":"en_US","strength":"IDENTICAL"}]f3 asc, f4 desc, [{"function":"lowercase"}]f5 desc;"""
    }

    def "contains sameElement"() {
        given:
        def q = Q.p("f1").containsSameElement(Q.p("stime").le(1).and("etime").gt(2))
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains sameElement(stime <= 1, etime > 2);"""
    }

    def "contains phrase/near/onear/equiv"() {
        given:
        def funcName = "contains${operator.capitalize()}"
        def q1 = Q.p("f1")."$funcName"("p1", "p2", "p3")
            .semicolon()
            .build()
        def q2 = Q.p("f1")."$funcName"(["p1", "p2", "p3"])
            .semicolon()
            .build()

        expect:
        q1 == """yql=select * from sources * where f1 contains ${operator}("p1", "p2", "p3");"""
        q2 == """yql=select * from sources * where f1 contains ${operator}("p1", "p2", "p3");"""

        where:
        operator | _
        "phrase" | _
        "near"   | _
        "onear"  | _
        "equiv"  | _
    }

    def "contains uri"() {
        given:
        def q = Q.p("f1").containsUri("https://test.uri")
                .semicolon()
                .build()

        expect:
        q == """yql=select * from sources * where f1 contains uri("https://test.uri");"""
    }

    def "contains uri with annotation"() {
        given:
        def q = Q.p("f1").containsUri(A.a("key", "value"), "https://test.uri")
                .semicolon()
                .build()

        expect:
        q == """yql=select * from sources * where f1 contains ([{"key":"value"}]uri("https://test.uri"));"""
    }

    def "use contains instead of contains equiv when input size is 1"() {
        def q = Q.p("f1").containsEquiv(["p1"])
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains "p1";"""
    }

    def "contains phrase/near/onear/equiv empty list should throw illegal argument exception"() {
        given:
        def funcName = "contains${operator.capitalize()}"

        when:
        def q = Q.p("f1")."$funcName"([])
            .semicolon()
            .build()

        then:
        thrown(IllegalArgumentException)

        where:
        operator | _
        "phrase" | _
        "near"   | _
        "onear"  | _
        "equiv"  | _
    }


    def "contains near/onear with annotation"() {
        given:
        def funcName = "contains${operator.capitalize()}"
        def q = Q.p("f1")."$funcName"(A.a("distance", 5), "p1", "p2", "p3")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains ([{"distance":5}]${operator}("p1", "p2", "p3"));"""

        where:
        operator | _
        "near"   | _
        "onear"  | _
    }

    def "basic group syntax"() {
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
        given:
        def q = Q.p("f1").contains("v1")
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
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains "v1" | all(group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))));"""
    }

    def "set group syntax string directly"() {
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
        given:
        def q = Q.p("f1").contains("v1")
            .group("all(group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))))")
            .semicolon()
            .build()

        expect:
        q == """yql=select * from sources * where f1 contains "v1" | all(group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))));"""
    }

    def "arbitrary annotations"() {
        given:
        def a = A.a("a1", "v1", "a2", 2, "a3", [k: "v", k2: 1], "a4", 4D, "a5", [1, 2, 3])
        expect:
        a.toString() == """{"a1":"v1","a2":2,"a3":{"k":"v","k2":1},"a4":4.0,"a5":[1,2,3]}"""
    }

    def "test programmability"() {
        given:
        def map = [a: "1", b: "2", c: "3"]

        when:
        Query q = map
            .entrySet()
            .stream()
            .map { entry -> Q.p(entry.key).contains(entry.value) }
            .reduce { q1, q2 -> q1.and(q2) }
            .get()

        then:
        q.semicolon().build() == """yql=select * from sources * where a contains "1" and b contains "2" and c contains "3";"""
    }

    def "test programmability 2"() {
        given:
        def map = [a: "1", b: "2", c: "3"]
        def q = Q.p()

        when:
        map.each { k, v ->
            q.and(Q.p(k).contains(v))
        }

        then:
        q.semicolon().build() == """yql=select * from sources * where a contains "1" and b contains "2" and c contains "3";"""
    }

    def "empty queries should not print out"() {
        given:
        def q = Q.p(Q.p(Q.p().andnot(Q.p()).and(Q.p()))).and("a").contains("1").semicolon().build()

        expect:
        q == """yql=select * from sources * where a contains "1";"""
    }

    def "validate positive search term of strings"() {
        given:
        def q = Q.p(Q.p("k1").contains("v1").and("k2").contains("v2").andnot("k3").contains("v3"))
            .andnot(Q.p("nk1").contains("nv1").and("nk2").contains("nv2").andnot("nk3").contains("nv3"))
            .and(Q.p("k4").contains("v4")
                .andnot(Q.p("k5").contains("v5").andnot("k6").contains("v6"))
            )

        expect:
        q.hasPositiveSearchField("k1")
        q.hasPositiveSearchField("k2")
        q.hasPositiveSearchField("nk3")
        q.hasPositiveSearchField("k6")
        q.hasPositiveSearchField("k6", "v6")
        !q.hasPositiveSearchField("k6", "v5")

        q.hasNegativeSearchField("k3")
        q.hasNegativeSearchField("nk1")
        q.hasNegativeSearchField("nk2")
        q.hasNegativeSearchField("k5")
        q.hasNegativeSearchField("k5", "v5")
        !q.hasNegativeSearchField("k5", "v4")
    }

    def "validate positive search term of user input"() {
        given:
        def q = Q.p(Q.ui("k1", "v1")).and(Q.ui("k2", "v2")).andnot(Q.ui("k3", "v3"))
            .andnot(Q.p(Q.ui("nk1", "nv1")).and(Q.ui("nk2", "nv2")).andnot(Q.ui("nk3", "nv3")))
            .and(Q.p(Q.ui("k4", "v4"))
                .andnot(Q.p(Q.ui("k5", "v5")).andnot(Q.ui("k6", "v6")))
            )

        expect:
        q.hasPositiveSearchField("k1")
        q.hasPositiveSearchField("k2")
        q.hasPositiveSearchField("nk3")
        q.hasPositiveSearchField("k6")
        q.hasPositiveSearchField("k6", "v6")
        !q.hasPositiveSearchField("k6", "v5")

        q.hasNegativeSearchField("k3")
        q.hasNegativeSearchField("nk1")
        q.hasNegativeSearchField("nk2")
        q.hasNegativeSearchField("k5")
        q.hasNegativeSearchField("k5", "v5")
        !q.hasNegativeSearchField("k5", "v4")
    }
}
