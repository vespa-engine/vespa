// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
                """
                        search test {
                            document test {
                                field a type int {
                                    indexing: attribute
                                    attribute: fast-search
                                }
                                field b type int {
                                    indexing: attribute
                                }
                            }
                            rank-profile parent {
                                match-phase {
                                    attribute: a
                                    max-hits: 120
                                    max-filter-coverage: 0.065
                                }
                                diversity {
                                    attribute: b
                                    min-groups: 74
                                    cutoff-factor: 17.3
                                    cutoff-strategy: strict
                                }
                            }
                        }
                        """);
        builder.build(true);
        Schema s = builder.getSchema();
        RankProfile parent = rankProfileRegistry.get(s, "parent");
        RankProfile.MatchPhaseSettings matchPhase = parent.getMatchPhase();
        RankProfile.DiversitySettings diversity = parent.getDiversity();
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
    private ApplicationBuilder getSearchBuilder(String diversityField) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema("""
                        search test {
                            document test {
                                field a type int {
                                    indexing: attribute
                                    attribute: fast-search
                                }""" +
                        diversityField +
                        """
                            }
                            rank-profile parent {
                                match-phase {
                                    diversity {
                                        attribute: b
                                        min-groups: 74
                                    }
                                    attribute: a
                                    max-hits: 120
                                }
                            }
                        }""");
        return builder;
    }
}
