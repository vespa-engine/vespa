// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 */
public class SoftTimeoutTestCase {

    @Test
    void testDefaultsInQuery() {
        Query query = new Query("?query=test");
        assertTrue(query.getRanking().getSoftTimeout().getEnable());
        assertNull(query.getRanking().getSoftTimeout().getFactor());
        assertNull(query.getRanking().getSoftTimeout().getTailcost());
    }

    @Test
    void testQueryOverride() {
        Query query = new Query("?query=test&ranking.softtimeout.factor=0.7&ranking.softtimeout.tailcost=0.3");
        assertTrue(query.getRanking().getSoftTimeout().getEnable());
        assertEquals(Double.valueOf(0.7), query.getRanking().getSoftTimeout().getFactor());
        assertEquals(Double.valueOf(0.3), query.getRanking().getSoftTimeout().getTailcost());
        query.prepare();
        assertNull(query.getRanking().getProperties().get("vespa.softtimeout.enable"));
        assertEquals("0.7", query.getRanking().getProperties().get("vespa.softtimeout.factor").get(0));
        assertEquals("0.3", query.getRanking().getProperties().get("vespa.softtimeout.tailcost").get(0));
    }

    @Test
    void testDisable() {
        Query query = new Query("?query=test&ranking.softtimeout.enable=false");
        assertFalse(query.getRanking().getSoftTimeout().getEnable());
        query.prepare();
        assertEquals("false", query.getRanking().getProperties().get("vespa.softtimeout.enable").get(0));
    }

    @Test
    void testEnable() {
        Query query = new Query("?query=test&ranking.softtimeout.enable=true");
        assertTrue(query.getRanking().getSoftTimeout().getEnable());
        query.prepare();
        assertEquals("true", query.getRanking().getProperties().get("vespa.softtimeout.enable").get(0));
    }

    private void verifyException(String key, String value) {
        try {
            new Query("?query=test&ranking.softtimeout."+key+"="+value);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.softtimeout." + key + "' to '" + value +"'", e.getMessage());
            assertEquals(key + " must be in the range [0.0, 1.0], got " + value, e.getCause().getMessage());
        }
    }

    @Test
    void testLimits() {
        verifyException("factor", "-0.1");
        verifyException("factor", "1.1");
        verifyException("tailcost", "-0.1");
        verifyException("tailcost", "1.1");
    }

}
