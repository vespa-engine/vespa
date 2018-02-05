// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.collections.Pair;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RankingExpressionInliningTestCase extends SearchDefinitionTestCase {

    @Test
    public void testMacroInliningPreserveArithemticOrdering() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type double { \n" +
                        "            indexing: attribute \n" +
                        "        }\n" +
                        "        field b type double { \n" +
                        "            indexing: attribute \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        constants {\n" +
                        "            p1: 7 \n" +
                        "            p2: 0 \n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: p1 * add\n" +
                        "        }\n" +
                        "        macro inline add() {\n" +
                        "            expression: 3 + attribute(a) + attribute(b) * mul3\n" +
                        "        }\n" +
                        "        macro inline mul3() {\n" +
                        "            expression: attribute(a) * 3 + singleif\n" +
                        "        }\n" +
                        "        macro inline singleif() {\n" +
                        "            expression: if (p1 < attribute(a), 1, 2) == 0\n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile child inherits parent {\n" +
                        "        macro inline add() {\n" +
                        "            expression: 9 + attribute(a)\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();

        RankProfile parent = rankProfileRegistry.getRankProfile(s, "parent").compile(new QueryProfileRegistry());
        assertEquals("7.0 * (3 + attribute(a) + attribute(b) * (attribute(a) * 3 + if (7.0 < attribute(a), 1, 2) == 0))",
                     parent.getFirstPhaseRanking().getRoot().toString());
        RankProfile child = rankProfileRegistry.getRankProfile(s, "child").compile(new QueryProfileRegistry());
        assertEquals("7.0 * (9 + attribute(a))",
                     child.getFirstPhaseRanking().getRoot().toString());
    }
    @Test
    public void testConstants() throws ParseException {
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
                        "    rank-profile parent {\n" +
                        "        constants {\n" +
                        "            p1: 7 \n" +
                        "            p2: 0 \n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: p1 + foo\n" +
                        "        }\n" +
                        "        second-phase {\n" +
                        "            expression: p2 * foo\n" +
                        "        }\n" +
                        "        macro inline foo() {\n" +
                        "            expression: 3 + p1 + p2\n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile child inherits parent {\n" +
                        "        first-phase {\n" +
                        "            expression: p1 + foo + baz + bar + arg(4.0)\n" +
                        "        }\n" +
                        "        constants {\n" +
                        "            p2: 2.0 \n" +
                        "        }\n" +
                        "        macro bar() {\n" +
                        "            expression: p2*p1\n" +
                        "        }\n" +
                        "        macro inline baz() {\n" +
                        "            expression: p2+p1+boz\n" +
                        "        }\n" +
                        "        macro inline boz() {\n" +
                        "            expression: 3.0\n" +
                        "        }\n" +
                        "        macro inline arg(a1) {\n" +
                        "            expression: a1*2\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search s = builder.getSearch();

        RankProfile parent = rankProfileRegistry.getRankProfile(s, "parent").compile(new QueryProfileRegistry());
        assertEquals("17.0", parent.getFirstPhaseRanking().getRoot().toString());
        assertEquals("0.0", parent.getSecondPhaseRanking().getRoot().toString());
        List<Pair<String, String>> parentRankProperties = new RawRankProfile(parent,
                                                                             new QueryProfileRegistry(),
                                                                             new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(foo).rankingScript,10.0)",
                     parentRankProperties.get(0).toString());
        assertEquals("(rankingExpression(firstphase).rankingScript,17.0)",
                     parentRankProperties.get(2).toString());
        assertEquals("(rankingExpression(secondphase).rankingScript,0.0)",
                     parentRankProperties.get(4).toString());

        RankProfile child = rankProfileRegistry.getRankProfile(s, "child").compile(new QueryProfileRegistry());
        assertEquals("31.0 + bar + arg(4.0)", child.getFirstPhaseRanking().getRoot().toString());
        assertEquals("24.0", child.getSecondPhaseRanking().getRoot().toString());
        List<Pair<String, String>> childRankProperties = new RawRankProfile(child,
                                                                            new QueryProfileRegistry(),
                                                                            new AttributeFields(s)).configProperties();
        assertEquals("(rankingExpression(foo).rankingScript,12.0)",
                     childRankProperties.get(0).toString());
        assertEquals("(rankingExpression(bar).rankingScript,14.0)",
                     childRankProperties.get(1).toString());
        assertEquals("(rankingExpression(boz).rankingScript,3.0)",
                     childRankProperties.get(2).toString());
        assertEquals("(rankingExpression(baz).rankingScript,9.0 + rankingExpression(boz))",
                     childRankProperties.get(3).toString());
        assertEquals("(rankingExpression(arg).rankingScript,a1 * 2)",
                     childRankProperties.get(4).toString());
        assertEquals("(rankingExpression(firstphase).rankingScript,31.0 + rankingExpression(bar) + rankingExpression(arg@))",
                     censorBindingHash(childRankProperties.get(7).toString()));
        assertEquals("(rankingExpression(secondphase).rankingScript,24.0)",
                     childRankProperties.get(9).toString());
    }

    /**
     * Expression evaluation has no stack so macro arguments are bound at config time creating a separate version of
     * each macro for each binding, using hashes to name the bound variants of the macro.
     * This method censors those hashes for string comparison.
     */
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
