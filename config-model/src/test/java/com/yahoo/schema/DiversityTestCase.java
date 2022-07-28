// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author baldersheim
 */
public class DiversityTestCase {
    @Test
    void testDiversity() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type int { \n" +
                        "            indexing: attribute \n" +
                        "            attribute: fast-search\n" +
                        "        }\n" +
                        "        field b type int {\n" +
                        "            indexing: attribute \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        match-phase {\n" +
                        "            diversity {\n" +
                        "                attribute: b\n" +
                        "                min-groups: 74\n" +
                        "                cutoff-factor: 17.3\n" +
                        "                cutoff-strategy: strict" +
                        "            }\n" +
                        "            attribute: a\n" +
                        "            max-hits: 120\n" +
                        "            max-filter-coverage: 0.065" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile.MatchPhaseSettings matchPhase = rankProfileRegistry.get(s, "parent").getMatchPhaseSettings();
        RankProfile.DiversitySettings diversity = matchPhase.getDiversity();
        assertEquals("b", diversity.getAttribute());
        assertEquals(74, diversity.getMinGroups());
        assertEquals(17.3, diversity.getCutoffFactor(), 1e-16);
        assertEquals(Diversity.CutoffStrategy.strict, diversity.getCutoffStrategy());
        assertEquals(120, matchPhase.getMaxHits());
        assertEquals("a", matchPhase.getAttribute());
        assertEquals(0.065, matchPhase.getMaxFilterCoverage(), 1e-16);
    }

    private static String getMessagePrefix() {
        return "In search definition 'test', rank-profile 'parent': diversity attribute 'b' ";
    }

    @Test
    void requireSingleNumericOrString() throws ParseException {
        ApplicationBuilder builder = getSearchBuilder("field b type predicate { indexing: attribute }");

        try {
            builder.build(true);
            fail("Should throw.");
        } catch (IllegalArgumentException e) {
            assertEquals(getMessagePrefix() + "must be single value numeric, or enumerated attribute, but it is 'predicate'", e.getMessage());
        }
    }

    @Test
    void requireSingle() throws ParseException {
        ApplicationBuilder builder = getSearchBuilder("field b type array<int> { indexing: attribute }");

        try {
            builder.build(true);
            fail("Should throw.");
        } catch (IllegalArgumentException e) {
            assertEquals(getMessagePrefix() + "must be single value numeric, or enumerated attribute, but it is 'Array<int>'", e.getMessage());
        }
    }
    private ApplicationBuilder getSearchBuilder(String diversity) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type int { \n" +
                        "            indexing: attribute \n" +
                        "            attribute: fast-search\n" +
                        "        }\n" +
                        diversity +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        match-phase {\n" +
                        "            diversity {\n" +
                        "                attribute: b\n" +
                        "                min-groups: 74\n" +
                        "            }\n" +
                        "            attribute: a\n" +
                        "            max-hits: 120\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        return builder;
    }
}
