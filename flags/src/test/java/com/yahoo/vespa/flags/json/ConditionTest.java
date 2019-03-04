// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import org.junit.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class ConditionTest {
    @Test
    public void testWhitelist() {
        String hostname1 = "host1";
        Condition condition = new Condition(Condition.Type.WHITELIST, FetchVector.Dimension.HOSTNAME,
                Stream.of(hostname1).collect(Collectors.toList()));
        assertFalse(condition.test(new FetchVector()));
        assertFalse(condition.test(new FetchVector().with(FetchVector.Dimension.APPLICATION_ID, "foo")));
        assertFalse(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, "bar")));
        assertTrue(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, hostname1)));
    }

    @Test
    public void testBlacklist() {
        String hostname1 = "host1";
        Condition condition = new Condition(Condition.Type.BLACKLIST, FetchVector.Dimension.HOSTNAME,
                Stream.of(hostname1).collect(Collectors.toList()));
        assertTrue(condition.test(new FetchVector()));
        assertTrue(condition.test(new FetchVector().with(FetchVector.Dimension.APPLICATION_ID, "foo")));
        assertTrue(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, "bar")));
        assertFalse(condition.test(new FetchVector().with(FetchVector.Dimension.HOSTNAME, hostname1)));
    }
}
