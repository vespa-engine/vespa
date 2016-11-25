package com.yahoo.search.query;

import com.yahoo.prelude.query.QueryException;
import com.yahoo.search.Query;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class SoftTimeoutTestCase {
    @Test
    public void testDefaultsInQuery() {
        Query query=new Query("?query=test");
        assertNull(query.getRanking().getSoftTimeout().getEnable());
        assertNull(query.getRanking().getSoftTimeout().getFactor());
        assertNull(query.getRanking().getSoftTimeout().getTailcost());
    }

    @Test
    public void testQueryOverride() {
        Query query=new Query("?query=test&ranking.softtimeout.enable&ranking.softtimeout.factor=0.7&ranking.softtimeout.tailcost=0.3");
        assertTrue(query.getRanking().getSoftTimeout().getEnable());
        assertEquals(Double.valueOf(0.7), query.getRanking().getSoftTimeout().getFactor());
        assertEquals(Double.valueOf(0.3), query.getRanking().getSoftTimeout().getTailcost());
    }

    private void verifyException(String key, String value) {
        try {
            new Query("?query=test&ranking.softtimeout."+key+"="+value);
            assertFalse(true);
        } catch (QueryException e) {
            assertEquals("Invalid request parameter", e.getMessage());
            assertEquals("Could not set 'ranking.softtimeout." + key + "' to '" + value +"'", e.getCause().getMessage());
            assertEquals(key + " must be in the range [0.0, 1.0]. It is " + value, e.getCause().getCause().getMessage());
        }
    }
    @Test
    public void testLimits() {
        verifyException("factor", "-0.1");
        verifyException("factor", "1.1");
        verifyException("tailcost", "-0.1");
        verifyException("tailcost", "1.1");
    }
}
