// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author baldersheim
 */
public class DiversityTestCase {
    private static void verifyDiversity(DeployLoggerStub logger, boolean atRankProfile, boolean atMatchPhase) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(logger, rankProfileRegistry);
        String diversitySpec = """
                diversity {
                    attribute: b
                    min-groups: 74
                    cutoff-factor: 17.3
                    cutoff-strategy: strict
                }
                """;
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
                                match-phase {""" +
                        (atMatchPhase ? diversitySpec : "") +
                        """
                                    attribute: a
                                    max-hits: 120
                                    max-filter-coverage: 0.065
                                }""" +
                        (atRankProfile ? diversitySpec : "") +
                                """
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
    @Test
    void testDiversity() throws ParseException {
        DeployLoggerStub logger = new DeployLoggerStub();
        verifyDiversity(logger, true, false);
        assertTrue(logger.entries.isEmpty());
        verifyDiversity(logger, false, true);
        assertEquals(1, logger.entries.size());
        assertEquals("'diversity is deprecated inside 'match-phase'. Specify it at 'rank-profile' level.", logger.entries.get(0).message);
        try {
            verifyDiversity(logger, true, true);
            fail("Should throw.");
        } catch (Exception e) {
            assertEquals("rank-profile 'parent' error: already has diversity", e.getMessage());
        }
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
        ApplicationBuilder builder = new ApplicationBuilder(new RankProfileRegistry());
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
                                    attribute: a
                                    max-hits: 120
                                }
                                diversity {
                                    attribute: b
                                    min-groups: 74
                                }
                            }
                        }""");
        return builder;
    }
}
