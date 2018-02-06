// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.collections.Pair;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.yolean.Exceptions;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RankingExpressionConstantsTestCase extends SearchDefinitionTestCase {

    @Test
    public void testConstants() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        QueryProfileRegistry queryProfileRegistry = new QueryProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        constants {\n" +
                        "            p1: 7 \n" +
                        "            p2: 0 \n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: p2 * (1.3 + p1 )\n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile child1 inherits parent {\n" +
                        "        first-phase {\n" +
                        "            expression: a + b + c \n" +
                        "        }\n" +
                        "        second-phase {\n" +
                        "            expression: a + p1 + c \n" +
                        "        }\n" +
                        "        constants {\n" +
                        "            a: 1.0 \n" +
                        "            b: 2 \n" +
                        "            c: 3.5 \n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile child2 inherits parent {\n" +
                        "        constants {\n" +
                        "            p2: 2.0 \n" +
                        "        }\n" +
                        "        macro foo() {\n" +
                        "            expression: p2*p1\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile parent = rankProfileRegistry.getRankProfile(s, "parent").compile(queryProfileRegistry);
        assertEquals("0.0", parent.getFirstPhaseRanking().getRoot().toString());

        RankProfile child1 = rankProfileRegistry.getRankProfile(s, "child1").compile(queryProfileRegistry);
        assertEquals("6.5", child1.getFirstPhaseRanking().getRoot().toString());
        assertEquals("11.5", child1.getSecondPhaseRanking().getRoot().toString());

        RankProfile child2 = rankProfileRegistry.getRankProfile(s, "child2").compile(queryProfileRegistry);
        assertEquals("16.6", child2.getFirstPhaseRanking().getRoot().toString());
        assertEquals("foo: 14.0", child2.getMacros().get("foo").getRankingExpression().toString());
        List<Pair<String, String>> rankProperties = new RawRankProfile(child2,
                                                                       queryProfileRegistry,
                                                                       new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(foo).rankingScript,14.0)", rankProperties.get(0).toString());
        assertEquals("(rankingExpression(firstphase).rankingScript,16.6)", rankProperties.get(2).toString());
    }

    @Test
    public void testNameCollision() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        constants {\n" +
                        "            c: 7 \n" +
                        "        }\n" +
                        "        macro c() {\n" +
                        "            expression: p2*p1\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        try {
            rankProfileRegistry.getRankProfile(s, "test").compile(new QueryProfileRegistry());
            fail("Should have caused an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'test' is invalid: Cannot have both a constant and macro named 'c'",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testNegativeLiteralArgument() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        macro POP_SLOW_SCORE() {\n" +
                        "            expression:  safeLog(popShareSlowDecaySignal, -9.21034037)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile profile = rankProfileRegistry.getRankProfile(s, "test");
        profile.parseExpressions(); // TODO: Do differently
        assertEquals("safeLog(popShareSlowDecaySignal,-9.21034037)", profile.getMacros().get("POP_SLOW_SCORE").getRankingExpression().getRoot().toString());
    }

    @Test
    public void testNegativeConstantArgument() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        constants {\n" +
                        "            myValue: -9.21034037\n" +
                        "        }\n" +
                        "        macro POP_SLOW_SCORE() {\n" +
                        "            expression:  safeLog(popShareSlowDecaySignal, myValue)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile profile = rankProfileRegistry.getRankProfile(s, "test");
        profile.parseExpressions(); // TODO: Do differently
        assertEquals("safeLog(popShareSlowDecaySignal,myValue)", profile.getMacros().get("POP_SLOW_SCORE").getRankingExpression().getRoot().toString());
        assertEquals("safeLog(popShareSlowDecaySignal,-9.21034037)",
                     profile.compile(new QueryProfileRegistry()).getMacros().get("POP_SLOW_SCORE").getRankingExpression().getRoot().toString());
    }

    @Test
    public void testConstantDivisorInMacro() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        macro rank_default(){\n" +
                        "            expression: k1 + (k2 + k3) / 100000000.0\n\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile profile = rankProfileRegistry.getRankProfile(s, "test");
        assertEquals("k1 + (k2 + k3) / 100000000.0",
                     profile.compile(new QueryProfileRegistry()).getMacros().get("rank_default").getRankingExpression().getRoot().toString());
    }

    @Test
    public void test3() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        macro rank_default(){\n" +
                        "            expression: 0.5+50*(attribute(rating_yelp)-3)\n\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile profile = rankProfileRegistry.getRankProfile(s, "test");
        assertEquals("0.5 + 50 * (attribute(rating_yelp) - 3)",
                     profile.compile(new QueryProfileRegistry()).getMacros().get("rank_default").getRankingExpression().getRoot().toString());
    }

}
