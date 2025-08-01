// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 */
public class MatchingTestCase {

    @Test
    void testDefaultsInQuery() {
        Query query = new Query("?query=test");
        assertNull(query.getRanking().getMatching().getTermwiseLimit());
        assertNull(query.getRanking().getMatching().getNumThreadsPerSearch());
        assertNull(query.getRanking().getMatching().getNumSearchPartitions());
        assertNull(query.getRanking().getMatching().getMinHitsPerThread());
        assertNull(query.getRanking().getMatching().getPostFilterThreshold());
        assertNull(query.getRanking().getMatching().getApproximateThreshold());
        assertNull(query.getRanking().getMatching().getTargetHitsMaxAdjustmentFactor());
        assertNull(query.getRanking().getMatching().getFilterThreshold());
        assertNull(query.getRanking().getMatching().getWeakAnd().getStopwordLimit());
        assertNull(query.getRanking().getMatching().getWeakAnd().getAdjustTarget());
        assertNull(query.getRanking().getMatching().getWeakAnd().getAllowDropAll());
    }

    @Test
    void testQueryOverrides() {
        Query query = new Query("?query=test" +
                "&ranking.matching.termwiseLimit=0.7" +
                "&ranking.matching.numThreadsPerSearch=17" +
                "&ranking.matching.numSearchPartitions=13" +
                "&ranking.matching.minHitsPerThread=3" +
                "&ranking.matching.postFilterThreshold=0.8" +
                "&ranking.matching.approximateThreshold=0.3" +
                "&ranking.matching.filterFirstThreshold=0.2" +
                "&ranking.matching.filterFirstExploration=0.35" +
                "&ranking.matching.explorationSlack=0.09" +
                "&ranking.matching.targetHitsMaxAdjustmentFactor=2.5" +
                "&ranking.matching.filterThreshold=0.7" +
                "&ranking.matching.weakand.stopwordLimit=0.6" +
                "&ranking.matching.weakand.adjustTarget=0.03" +
                "&ranking.matching.weakand.allowDropAll=true");
        assertEquals(Double.valueOf(0.7), query.getRanking().getMatching().getTermwiseLimit());
        assertEquals(Integer.valueOf(17), query.getRanking().getMatching().getNumThreadsPerSearch());
        assertEquals(Integer.valueOf(13), query.getRanking().getMatching().getNumSearchPartitions());
        assertEquals(Integer.valueOf(3), query.getRanking().getMatching().getMinHitsPerThread());
        assertEquals(Double.valueOf(0.8), query.getRanking().getMatching().getPostFilterThreshold());
        assertEquals(Double.valueOf(0.3), query.getRanking().getMatching().getApproximateThreshold());
        assertEquals(Double.valueOf(0.2), query.getRanking().getMatching().getFilterFirstThreshold());
        assertEquals(Double.valueOf(0.35), query.getRanking().getMatching().getFilterFirstExploration());
        assertEquals(Double.valueOf(0.09), query.getRanking().getMatching().getExplorationSlack());
        assertEquals(Double.valueOf(2.5), query.getRanking().getMatching().getTargetHitsMaxAdjustmentFactor());
        assertEquals(Double.valueOf(0.7), query.getRanking().getMatching().getFilterThreshold());
        assertEquals(Double.valueOf(0.6), query.getRanking().getMatching().getWeakAnd().getStopwordLimit());
        assertEquals(Double.valueOf(0.03), query.getRanking().getMatching().getWeakAnd().getAdjustTarget());
        assertEquals(Boolean.valueOf(true), query.getRanking().getMatching().getWeakAnd().getAllowDropAll());

        query.prepare();
        assertEquals("0.7", query.getRanking().getProperties().get("vespa.matching.termwise_limit").get(0));
        assertEquals("17", query.getRanking().getProperties().get("vespa.matching.numthreadspersearch").get(0));
        assertEquals("13", query.getRanking().getProperties().get("vespa.matching.numsearchpartitions").get(0));
        assertEquals("3", query.getRanking().getProperties().get("vespa.matching.minhitsperthread").get(0));
        assertEquals("0.8", query.getRanking().getProperties().get("vespa.matching.global_filter.upper_limit").get(0));
        assertEquals("0.3", query.getRanking().getProperties().get("vespa.matching.global_filter.lower_limit").get(0));
        assertEquals("0.2", query.getRanking().getProperties().get("vespa.matching.nns.filter_first_upper_limit").get(0));
        assertEquals("0.35", query.getRanking().getProperties().get("vespa.matching.nns.filter_first_exploration").get(0));
        assertEquals("0.09", query.getRanking().getProperties().get("vespa.matching.nns.exploration_slack").get(0));
        assertEquals("2.5", query.getRanking().getProperties().get("vespa.matching.nns.target_hits_max_adjustment_factor").get(0));
        assertEquals("0.7", query.getRanking().getProperties().get("vespa.matching.filter_threshold").get(0));
        assertEquals("0.6", query.getRanking().getProperties().get("vespa.matching.weakand.stop_word_drop_limit").get(0));
        assertEquals("0.03", query.getRanking().getProperties().get("vespa.matching.weakand.stop_word_adjust_limit").get(0));
        assertEquals("true", query.getRanking().getProperties().get("vespa.matching.weakand.allow_drop_all").get(0));
    }

    @Test
    void testBackwardsCompatibleQueryOverrides() {
        // The lowercase aliases are supported to provide backwards compatibility of the properties that was wrongly named in the first place.
        Query query = new Query("?query=test" +
                "&ranking.matching.termwiselimit=0.7" +
                "&ranking.matching.numthreadspersearch=17" +
                "&ranking.matching.numsearchpartitions=13" +
                "&ranking.matching.minhitsperthread=3");
        assertEquals(Double.valueOf(0.7), query.getRanking().getMatching().getTermwiseLimit());
        assertEquals(Integer.valueOf(17), query.getRanking().getMatching().getNumThreadsPerSearch());
        assertEquals(Integer.valueOf(13), query.getRanking().getMatching().getNumSearchPartitions());
        assertEquals(Integer.valueOf(3), query.getRanking().getMatching().getMinHitsPerThread());
    }

    private void verifyException(String key, String expectKey, String value) {
        try {
            new Query("?query=test&ranking.matching."+key+"="+value);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.matching." + key + "' to '" + value +"'", e.getMessage());
            assertEquals(expectKey + " must be in the range [0.0, 1.0]. It is " + value, e.getCause().getMessage());
        }
    }

    private void verifyException(String key, String value) {
        verifyException(key, key, value);
    }

    @Test
    void testLimits() {
        verifyException("termwiseLimit", "-0.1");
        verifyException("termwiseLimit", "1.1");
        verifyException("filterThreshold", "-0.1");
        verifyException("filterThreshold", "1.1");
        verifyException("weakand.stopwordLimit", "stopwordLimit", "-0.1");
        verifyException("weakand.stopwordLimit", "stopwordLimit", "1.1");
        verifyException("weakand.adjustTarget", "adjustTarget", "-0.1");
        verifyException("weakand.adjustTarget", "adjustTarget", "1.1");
    }

}
