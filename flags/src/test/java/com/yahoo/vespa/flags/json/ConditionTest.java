// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hakonhall
 */
public class ConditionTest {
    @Test
    void testWhitelist() {
        String hostname1 = "host1";
        var params = new Condition.CreateParams(FetchVector.Dimension.HOSTNAME).withValues(hostname1);
        Condition condition = WhitelistCondition.create(params);
        assertFalse(condition.test(new FetchVector()));
        assertFalse(condition.test(new FetchVector().with(FetchVector.Dimension.APPLICATION_ID, "foo")));
        assertFalse(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, "bar")));
        assertTrue(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, hostname1)));
    }

    @Test
    void testBlacklist() {
        String hostname1 = "host1";
        var params = new Condition.CreateParams(FetchVector.Dimension.HOSTNAME).withValues(hostname1);
        Condition condition = BlacklistCondition.create(params);
        assertTrue(condition.test(new FetchVector()));
        assertTrue(condition.test(new FetchVector().with(FetchVector.Dimension.APPLICATION_ID, "foo")));
        assertTrue(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, "bar")));
        assertFalse(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, hostname1)));
    }

    @Test
    void testRelational() {
        verifyVespaVersionFor("<", true, false, false);
        verifyVespaVersionFor("<=", true, true, false);
        verifyVespaVersionFor(">", false, false, true);
        verifyVespaVersionFor(">=", false, true, true);

        // Test with empty fetch vector along vespa version dimension (this should never happen as the
        // version is always available through Vtag, although Vtag has a dummy version number for e.g.
        // locally run unit tests that hasn't set the release Vespa version).
        var params = new Condition.CreateParams(FetchVector.Dimension.VESPA_VERSION).withPredicate(">=7.1.2");
        Condition condition = RelationalCondition.create(params);
        assertFalse(condition.test(new FetchVector()));
    }

    private void verifyVespaVersionFor(String operator, boolean whenLess, boolean whenEqual, boolean whenGreater) {
        assertEquals(whenLess, vespaVersionCondition("7.2.4", operator + "7.3.4"));
        assertEquals(whenEqual, vespaVersionCondition("7.3.4", operator + "7.3.4"));
        assertEquals(whenGreater, vespaVersionCondition("7.4.4", operator + "7.3.4"));
    }

    private boolean vespaVersionCondition(String vespaVersion, String predicate) {
        var params = new Condition.CreateParams(FetchVector.Dimension.VESPA_VERSION).withPredicate(predicate);
        Condition condition = RelationalCondition.create(params);
        return condition.test(new FetchVector().with(FetchVector.Dimension.VESPA_VERSION, vespaVersion));
    }
}
