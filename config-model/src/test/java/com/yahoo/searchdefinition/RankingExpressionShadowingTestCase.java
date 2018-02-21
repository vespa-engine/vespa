// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.collections.Pair;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RankingExpressionShadowingTestCase extends SearchDefinitionTestCase {

    @Test
    public void testBasicMacroShadowing() throws ParseException {
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
                        "        macro sin(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: sin(2)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile(new QueryProfileRegistry());
        List<Pair<String, String>> testRankProperties = new RawRankProfile(test,
                                                                           new QueryProfileRegistry(),
                                                                           new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(sin).rankingScript,x * x)",
                     testRankProperties.get(0).toString());
        assertEquals("(rankingExpression(sin@).rankingScript,2 * 2)",
                     censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(vespa.rank.firstphase,rankingExpression(sin@))",
                     censorBindingHash(testRankProperties.get(2).toString()));
    }


    @Test
    public void testMultiLevelMacroShadowing() throws ParseException {
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
                        "        macro tan(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        macro cos(x) {\n" +
                        "            expression: tan(x)\n" +
                        "        }\n" +
                        "        macro sin(x) {\n" +
                        "            expression: cos(x)\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: sin(2)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile(new QueryProfileRegistry());
        List<Pair<String, String>> testRankProperties = new RawRankProfile(test,
                                                                           new QueryProfileRegistry(),
                                                                           new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(tan).rankingScript,x * x)",
                     testRankProperties.get(0).toString());
        assertEquals("(rankingExpression(tan@).rankingScript,x * x)",
                     censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(rankingExpression(cos).rankingScript,rankingExpression(tan@))",
                     censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("(rankingExpression(cos@).rankingScript,rankingExpression(tan@))",
                     censorBindingHash(testRankProperties.get(3).toString()));
        assertEquals("(rankingExpression(sin).rankingScript,rankingExpression(cos@))",
                     censorBindingHash(testRankProperties.get(4).toString()));
        assertEquals("(rankingExpression(tan@).rankingScript,2 * 2)",
                     censorBindingHash(testRankProperties.get(5).toString()));
        assertEquals("(rankingExpression(cos@).rankingScript,rankingExpression(tan@))",
                     censorBindingHash(testRankProperties.get(6).toString()));
        assertEquals("(rankingExpression(sin@).rankingScript,rankingExpression(cos@))",
                     censorBindingHash(testRankProperties.get(7).toString()));
        assertEquals("(vespa.rank.firstphase,rankingExpression(sin@))",
                     censorBindingHash(testRankProperties.get(8).toString()));
    }


    @Test
    public void testMacroShadowingArguments() throws ParseException {
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
                        "        macro sin(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: cos(sin(2*2)) + sin(cos(1+4))\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile(new QueryProfileRegistry());
        List<Pair<String, String>> testRankProperties = new RawRankProfile(test,
                                                                           new QueryProfileRegistry(),
                                                                           new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(sin).rankingScript,x * x)",
                     testRankProperties.get(0).toString());
        assertEquals("(rankingExpression(sin@).rankingScript,4.0 * 4.0)",
                     censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(rankingExpression(sin@).rankingScript,cos(5.0) * cos(5.0))",
                     censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("(vespa.rank.firstphase,rankingExpression(firstphase))",
                     censorBindingHash(testRankProperties.get(3).toString()));
        assertEquals("(rankingExpression(firstphase).rankingScript,cos(rankingExpression(sin@)) + rankingExpression(sin@))",
                     censorBindingHash(testRankProperties.get(4).toString()));
    }

    @Test
    public void testNeuralNetworkSetup() throws ParseException {
        // Note: the type assigned to query profile and constant tensors here is not the correct type
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        QueryProfileRegistry queryProfiles = queryProfileWith("query(q)", "tensor(x[])");
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry, queryProfiles);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        macro relu(x) {\n" + // relu is a built in function, redefined here
                        "            expression: max(1.0, x)\n" +
                        "        }\n" +
                        "        macro hidden_layer() {\n" +
                        "            expression: relu(sum(query(q) * constant(W_hidden), input) + constant(b_input))\n" +
                        "        }\n" +
                        "        macro final_layer() {\n" +
                        "            expression: sigmoid(sum(hidden_layer * constant(W_final), hidden) + constant(b_final))\n" +
                        "        }\n" +
                        "        second-phase {\n" +
                        "            expression: sum(final_layer)\n" +
                        "        }\n" +
                        "    }\n" +
                        "    constant W_hidden {\n" +
                        "        type: tensor(x[])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "    constant b_input {\n" +
                        "        type: tensor(x[])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "    constant W_final {\n" +
                        "        type: tensor(x[])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "    constant b_final {\n" +
                        "        type: tensor(x[])\n" +
                        "        file: ignored.json\n" +
                        "    }\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile(queryProfiles);
        List<Pair<String, String>> testRankProperties = new RawRankProfile(test,
                                                                           queryProfiles,
                                                                           new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(relu).rankingScript,max(1.0,x))",
                     testRankProperties.get(0).toString());
        assertEquals("(rankingExpression(relu@).rankingScript,max(1.0,reduce(query(q) * constant(W_hidden), sum, input) + constant(b_input)))",
                     censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("(rankingExpression(hidden_layer).rankingScript,rankingExpression(relu@))",
                     censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("(rankingExpression(final_layer).rankingScript,sigmoid(reduce(rankingExpression(hidden_layer) * constant(W_final), sum, hidden) + constant(b_final)))",
                     testRankProperties.get(3).toString());
        assertEquals("(vespa.rank.secondphase,rankingExpression(secondphase))",
                     testRankProperties.get(4).toString());
        assertEquals("(rankingExpression(secondphase).rankingScript,reduce(rankingExpression(final_layer), sum))",
                     testRankProperties.get(5).toString());
    }

    private QueryProfileRegistry queryProfileWith(String field, String type) {
        QueryProfileType queryProfileType = new QueryProfileType("root");
        queryProfileType.addField(new FieldDescription(field, type));
        QueryProfileRegistry queryProfileRegistry = new QueryProfileRegistry();
        queryProfileRegistry.getTypeRegistry().register(queryProfileType);
        QueryProfile profile = new QueryProfile("default");
        profile.setType(queryProfileType);
        queryProfileRegistry.register(profile);
        return queryProfileRegistry;
    }

    private String censorBindingHash(String s) {
        StringBuilder b = new StringBuilder();
        boolean areInHash = false;
        for (int i = 0; i < s.length() ; i++) {
            char current = s.charAt(i);
            if ( ! Character.isLetterOrDigit(current)) // end of hash
                areInHash = false;
            if ( ! areInHash)
                b.append(current);
            if (current == '@') // start of hash
                areInHash = true;
        }
        return b.toString();
    }

}
