// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class MatchPhaseTestCase {

    @Test
    void testDefaults() {
        MatchPhase matchPhase = new MatchPhase();
        assertNull(matchPhase.getAttribute());
        assertFalse(matchPhase.getAscending());
        assertNull(matchPhase.getMaxHits());
        assertNull(matchPhase.getTotalMaxHits());
        assertEquals(0.2, matchPhase.getMaxFilterCoverage());
        assertEquals(new Diversity(), matchPhase.getDiversity());
    }

    @Test
    void testMaxFilterCoverageRange() {
        MatchPhase matchPhase = new MatchPhase();
        matchPhase.setMaxFilterCoverage(0.0);
        assertEquals(0.0, matchPhase.getMaxFilterCoverage());
        matchPhase.setMaxFilterCoverage(1.0);
        assertEquals(1.0, matchPhase.getMaxFilterCoverage());

        var e = assertThrows(IllegalInputException.class, () -> matchPhase.setMaxFilterCoverage(-0.1));
        assertEquals("maxFilterCoverage must be in the range [0.0, 1.0], but is -0.1", e.getMessage());
        assertThrows(IllegalInputException.class, () -> matchPhase.setMaxFilterCoverage(1.1));
        assertEquals(1.0, matchPhase.getMaxFilterCoverage());
    }

    @Test
    void testPrepareDoesNothingWithoutAttributeAndMaxHits() {
        RankProperties properties = new RankProperties();
        new MatchPhase().prepare(properties);
        assertTrue(properties.asMap().isEmpty());

        MatchPhase onlyAttribute = new MatchPhase();
        onlyAttribute.setAttribute("foo");
        onlyAttribute.prepare(properties);
        assertTrue(properties.asMap().isEmpty());

        MatchPhase onlyMaxHits = new MatchPhase();
        onlyMaxHits.setMaxHits(100);
        onlyMaxHits.prepare(properties);
        assertTrue(properties.asMap().isEmpty());

        MatchPhase onlyTotalMaxHits = new MatchPhase();
        onlyTotalMaxHits.setTotalMaxHits(1000);
        onlyTotalMaxHits.prepare(properties);
        assertTrue(properties.asMap().isEmpty());
    }

    @Test
    void testPrepare() {
        MatchPhase matchPhase = new MatchPhase();
        matchPhase.setAttribute("foo");
        matchPhase.setMaxHits(100);

        RankProperties properties = new RankProperties();
        matchPhase.prepare(properties);
        assertEquals(List.of("foo"), properties.get("vespa.matchphase.degradation.attribute"));
        assertEquals(List.of("0.2"), properties.get("vespa.matchphase.degradation.maxfiltercoverage"));
        assertNull(properties.get("vespa.matchphase.degradation.ascendingorder"));
        assertNull(properties.get("vespa.matchphase.diversity.attribute"));
        assertEquals(2, properties.asMap().size());
    }

    @Test
    void testPrepareWithTotalMaxHits() {
        MatchPhase matchPhase = new MatchPhase();
        matchPhase.setAttribute("foo");
        matchPhase.setTotalMaxHits(1000);

        RankProperties properties = new RankProperties();
        matchPhase.prepare(properties);
        assertEquals(List.of("foo"), properties.get("vespa.matchphase.degradation.attribute"));
        assertEquals(List.of("0.2"), properties.get("vespa.matchphase.degradation.maxfiltercoverage"));
    }

    @Test
    void testPrepareWithAscendingAndDiversity() {
        MatchPhase matchPhase = new MatchPhase();
        matchPhase.setAttribute("foo");
        matchPhase.setMaxHits(100);
        matchPhase.setAscending(true);
        matchPhase.setMaxFilterCoverage(0.7);
        matchPhase.getDiversity().setAttribute("bar");
        matchPhase.getDiversity().setMinGroups(10);

        RankProperties properties = new RankProperties();
        matchPhase.prepare(properties);
        assertEquals(List.of("foo"), properties.get("vespa.matchphase.degradation.attribute"));
        assertEquals(List.of("true"), properties.get("vespa.matchphase.degradation.ascendingorder"));
        assertEquals(List.of("0.7"), properties.get("vespa.matchphase.degradation.maxfiltercoverage"));
        assertEquals(List.of("bar"), properties.get("vespa.matchphase.diversity.attribute"));
        assertEquals(List.of("10"), properties.get("vespa.matchphase.diversity.mingroups"));
    }

    @Test
    void testSetFromQueryParameters() {
        Query query = new Query("?query=test" +
                                "&ranking.matchPhase.attribute=foo" +
                                "&ranking.matchPhase.ascending=true" +
                                "&ranking.matchPhase.maxHits=100" +
                                "&ranking.matchPhase.totalMaxHits=1000" +
                                "&ranking.matchPhase.maxFilterCoverage=0.5" +
                                "&ranking.matchPhase.diversity.attribute=bar" +
                                "&ranking.matchPhase.diversity.minGroups=10");
        MatchPhase matchPhase = query.getRanking().getMatchPhase();
        assertEquals("foo", matchPhase.getAttribute());
        assertTrue(matchPhase.getAscending());
        assertEquals(100L, matchPhase.getMaxHits());
        assertEquals(1000L, matchPhase.getTotalMaxHits());
        assertEquals(0.5, matchPhase.getMaxFilterCoverage());
        assertEquals("bar", matchPhase.getDiversity().getAttribute());
        assertEquals(10L, matchPhase.getDiversity().getMinGroups());
    }

}
