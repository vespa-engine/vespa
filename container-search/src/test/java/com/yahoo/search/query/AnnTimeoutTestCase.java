// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author boeker
 */
public class AnnTimeoutTestCase {

    @Test
    void testDefaultsInQuery() {
        Query query = new Query("?query=test");
        assertFalse(query.getRanking().getMatching().getAnnTimeout().getEnable());
        assertNull(query.getRanking().getMatching().getAnnTimeout().getFactor());
    }

    @Test
    void testQueryOverride() {
        Query query = new Query("?query=test&ranking.matching.anntimeout.factor=0.1");
        assertFalse(query.getRanking().getMatching().getAnnTimeout().getEnable());
        assertEquals(Double.valueOf(0.1), query.getRanking().getMatching().getAnnTimeout().getFactor());
        query.prepare();
        assertNull(query.getRanking().getProperties().get("vespa.matching.nns.anntimeout.enable"));
        assertEquals("0.1", query.getRanking().getProperties().get("vespa.matching.nns.anntimeout.factor").get(0));
    }

    @Test
    void testDisable() {
        Query query = new Query("?query=test&ranking.matching.anntimeout.enable=false");
        assertFalse(query.getRanking().getMatching().getAnnTimeout().getEnable());
        query.prepare();
        assertEquals("false", query.getRanking().getProperties().get("vespa.matching.nns.anntimeout.enable").get(0));
    }

    @Test
    void testEnable() {
        Query query = new Query("?query=test&ranking.matching.anntimeout.enable=true");
        assertTrue(query.getRanking().getMatching().getAnnTimeout().getEnable());
        query.prepare();
        assertEquals("true", query.getRanking().getProperties().get("vespa.matching.nns.anntimeout.enable").get(0));
    }

    private void verifyException(String value) {
        try {
            new Query("?query=test&ranking.matching.anntimeout.factor="+value);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.matching.anntimeout.factor'", e.getMessage());
            assertEquals("factor must be in the range [0.0, 1.0], got " + value, e.getCause().getMessage());
        }
    }

    @Test
    void testLimits() {
        verifyException("-0.1");
        verifyException("1.1");
    }

}
