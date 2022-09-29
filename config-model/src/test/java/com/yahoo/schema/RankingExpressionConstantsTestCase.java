// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.collections.Pair;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.yolean.Exceptions;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.RawRankProfile;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RankingExpressionConstantsTestCase extends AbstractSchemaTestCase {

    @Test
    void testConstants() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        QueryProfileRegistry queryProfileRegistry = new QueryProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "schema test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        constants {\n" +
                        "            p1 double: 7 \n" +
                        "            constant(p2) double: 0 \n" +
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
                        "            constant(b): 2 \n" +
                        "            c: 3.5 \n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile child2 inherits parent {\n" +
                        "        constants {\n" +
                        "            p2: 2.0 \n" +
                        "        }\n" +
                        "        function foo() {\n" +
                        "            expression: p2*p1\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile parent = rankProfileRegistry.get(s, "parent").compile(queryProfileRegistry, new ImportedMlModels());
        assertEquals("0.0", parent.getFirstPhaseRanking().getRoot().toString());

        RankProfile child1 = rankProfileRegistry.get(s, "child1").compile(queryProfileRegistry, new ImportedMlModels());
        assertEquals("6.5", child1.getFirstPhaseRanking().getRoot().toString());
        assertEquals("11.5", child1.getSecondPhaseRanking().getRoot().toString());

        RankProfile child2 = rankProfileRegistry.get(s, "child2").compile(queryProfileRegistry, new ImportedMlModels());
        assertEquals("16.6", child2.getFirstPhaseRanking().getRoot().toString());
        assertEquals("foo: 14.0", child2.getFunctions().get("foo").function().getBody().toString());
        List<Pair<String, String>> rankProperties = new RawRankProfile(child2,
                new LargeRankingExpressions(new MockFileRegistry()),
                queryProfileRegistry,
                new ImportedMlModels(),
                new AttributeFields(s),
                new TestProperties()).configProperties();
        assertEquals("(rankingExpression(foo).rankingScript, 14.0)", rankProperties.get(0).toString());
        assertEquals("(rankingExpression(firstphase).rankingScript, 16.6)", rankProperties.get(2).toString());
    }

    @Test
    void testNameCollision() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "schema test {\n" +
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
                        "        function c() {\n" +
                        "            expression: p2*p1\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        try {
            rankProfileRegistry.get(s, "test").compile(new QueryProfileRegistry(), new ImportedMlModels());
            fail("Should have caused an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Rank profile 'test' is invalid: Cannot have both a constant and function named 'c'",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testNegativeLiteralArgument() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function POP_SLOW_SCORE() {\n" +
                        "            expression:  safeLog(popShareSlowDecaySignal, -9.21034037)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile profile = rankProfileRegistry.get(s, "test");
        assertEquals("safeLog(popShareSlowDecaySignal,-9.21034037)", profile.getFunctions().get("POP_SLOW_SCORE").function().getBody().getRoot().toString());
    }

    @Test
    void testNegativeConstantArgument() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "schema test {\n" +
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
                        "        function POP_SLOW_SCORE() {\n" +
                        "            expression:  safeLog(popShareSlowDecaySignal, myValue)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile profile = rankProfileRegistry.get(s, "test");
        assertEquals("safeLog(popShareSlowDecaySignal,myValue)", profile.getFunctions().get("POP_SLOW_SCORE").function().getBody().getRoot().toString());
        assertEquals("safeLog(popShareSlowDecaySignal,-9.21034037)",
                profile.compile(new QueryProfileRegistry(), new ImportedMlModels()).getFunctions().get("POP_SLOW_SCORE").function().getBody().getRoot().toString());
    }

    @Test
    void testConstantDivisorInFunction() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function rank_default(){\n" +
                        "            expression: k1 + (k2 + k3) / 100000000.0\n\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile profile = rankProfileRegistry.get(s, "test");
        assertEquals("k1 + (k2 + k3) / 1.0E8",
                profile.compile(new QueryProfileRegistry(), new ImportedMlModels()).getFunctions().get("rank_default").function().getBody().getRoot().toString());
    }

    @Test
    void test3() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field rating_yelp type int {" +
                        "          indexing: attribute" +
                        "        }" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        function rank_default(){\n" +
                        "            expression: 0.5+50*(attribute(rating_yelp)-3)\n\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile profile = rankProfileRegistry.get(s, "test");
        assertEquals("0.5 + 50 * (attribute(rating_yelp) - 3)",
                profile.compile(new QueryProfileRegistry(), new ImportedMlModels()).getFunctions().get("rank_default").function().getBody().getRoot().toString());
    }

}
