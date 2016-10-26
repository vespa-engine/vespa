package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
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
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile();
        List<Map.Entry<String, Object>> testRankProperties = new ArrayList<>(new RawRankProfile(test, new AttributeFields(s)).configProperties().entrySet());
        for (Object o : testRankProperties)
            System.out.println(o);
        assertEquals("rankingExpression(sin).rankingScript.part0=x * x", testRankProperties.get(0).toString());
        assertEquals("rankingExpression(sin@).rankingScript.part1=2 * 2", censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("vespa.rank.firstphase=rankingExpression(sin@)", censorBindingHash(testRankProperties.get(2).toString()));
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
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile();
        List<Map.Entry<String, Object>> testRankProperties = new ArrayList<>(new RawRankProfile(test, new AttributeFields(s)).configProperties().entrySet());
        for (Object o : testRankProperties)
            System.out.println(o);
        assertEquals("rankingExpression(tan).rankingScript.part0=x * x", testRankProperties.get(0).toString());
        assertEquals("rankingExpression(tan@).rankingScript.part1=x * x", censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("rankingExpression(cos).rankingScript.part2=rankingExpression(tan@)", censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("rankingExpression(cos@).rankingScript.part3=rankingExpression(tan@)", censorBindingHash(testRankProperties.get(3).toString()));
        assertEquals("rankingExpression(sin).rankingScript.part4=rankingExpression(cos@)", censorBindingHash(testRankProperties.get(4).toString()));
        assertEquals("rankingExpression(tan@).rankingScript.part5=2 * 2", censorBindingHash(testRankProperties.get(5).toString()));
        assertEquals("rankingExpression(cos@).rankingScript.part6=rankingExpression(tan@)", censorBindingHash(testRankProperties.get(6).toString()));
        assertEquals("rankingExpression(sin@).rankingScript.part7=rankingExpression(cos@)", censorBindingHash(testRankProperties.get(7).toString()));
        assertEquals("vespa.rank.firstphase=rankingExpression(sin@)", censorBindingHash(testRankProperties.get(8).toString()));
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
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile();
        List<Map.Entry<String, Object>> testRankProperties = new ArrayList<>(new RawRankProfile(test, new AttributeFields(s)).configProperties().entrySet());
        for (Object o : testRankProperties)
            System.out.println(o);
        assertEquals("rankingExpression(sin).rankingScript.part0=x * x", testRankProperties.get(0).toString());
        assertEquals("rankingExpression(sin@).rankingScript.part1=4.0 * 4.0", censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("rankingExpression(sin@).rankingScript.part2=cos(5.0) * cos(5.0)", censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("vespa.rank.firstphase=rankingExpression(firstphase)", censorBindingHash(testRankProperties.get(3).toString()));
        assertEquals("rankingExpression(firstphase).rankingScript=cos(rankingExpression(sin@)) + rankingExpression(sin@)", censorBindingHash(testRankProperties.get(4).toString()));
    }


    @Test
    public void testNeuralNetworkSetup() throws ParseException {
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
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile();
        List<Map.Entry<String, Object>> testRankProperties = new ArrayList<>(new RawRankProfile(test, new AttributeFields(s)).configProperties().entrySet());
        for (Object o : testRankProperties)
            System.out.println(o);
        assertEquals("rankingExpression(relu).rankingScript.part0=max(1.0,x)", testRankProperties.get(0).toString());
        assertEquals("rankingExpression(relu@).rankingScript.part1=max(1.0,sum(query(q) * constant(W_hidden), input) + constant(b_input))", censorBindingHash(testRankProperties.get(1).toString()));
        assertEquals("rankingExpression(hidden_layer).rankingScript.part2=rankingExpression(relu@)", censorBindingHash(testRankProperties.get(2).toString()));
        assertEquals("rankingExpression(final_layer).rankingScript.part3=sigmoid(sum(rankingExpression(hidden_layer) * constant(W_final), hidden) + constant(b_final))", testRankProperties.get(3).toString());
        assertEquals("vespa.rank.secondphase=rankingExpression(secondphase)", testRankProperties.get(4).toString());
        assertEquals("rankingExpression(secondphase).rankingScript=sum(rankingExpression(final_layer))", testRankProperties.get(5).toString());
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
