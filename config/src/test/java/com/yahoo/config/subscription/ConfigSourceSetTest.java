// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import org.junit.Test;
import org.junit.After;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="gv@yahoo-inc.com">G. Voldengen</a>
 */
public class ConfigSourceSetTest {

    @Test
    public void testEquals() {
        assertEquals(new ConfigSourceSet(), new ConfigSourceSet());
        assertNotEquals(new ConfigSourceSet(), new ConfigSourceSet(new String[]{"a"}));

        assertEquals(new ConfigSourceSet(new String[]{"a"}), new ConfigSourceSet(new String[]{"a"}));
        assertEquals(new ConfigSourceSet(new String[]{"a"}), new ConfigSourceSet(new String[]{"  A  "}));
        assertEquals(new ConfigSourceSet(new String[]{"a"}), new ConfigSourceSet(new String[]{"A", "a"}));
        assertEquals(new ConfigSourceSet(new String[]{"A"}), new ConfigSourceSet(new String[]{"a", " a  "}));

        assertNotEquals(new ConfigSourceSet(new String[]{"a"}), new ConfigSourceSet(new String[]{"b"}));
        assertNotEquals(new ConfigSourceSet(new String[]{"a"}), new ConfigSourceSet(new String[]{"a", "b"}));

        assertEquals(new ConfigSourceSet(new String[]{"a", "b"}), new ConfigSourceSet(new String[]{"a", "b"}));
        assertEquals(new ConfigSourceSet(new String[]{"b", "a"}), new ConfigSourceSet(new String[]{"a", "b"}));
        assertEquals(new ConfigSourceSet(new String[]{"A", " b"}), new ConfigSourceSet(new String[]{"a ", "B"}));
        assertEquals(new ConfigSourceSet(new String[]{"b", "a", "c"}), new ConfigSourceSet(new String[]{"a", "b", "c"}));

        assertNotEquals(new ConfigSourceSet(new String[]{"a", "b"}), new ConfigSourceSet(new String[]{"b", "c"}));
        assertNotEquals("foo", new ConfigSourceSet());
    }

    @Test
    public void testIterationOrder() {
        String[] hosts = new String[]{"primary", "fallback", "last-resort"};
        ConfigSourceSet css = new ConfigSourceSet(hosts);

        Set<String> sources = css.getSources();
        assertEquals(hosts.length, sources.size());
        int i = 0;
        for (String s : sources) {
            assertEquals(hosts[i++], s);
        }
    }

    @Test
    public void testDefaultSourceFromProperty() {
        // TODO: Unable to set environment, so only able to test property usage for now
        System.setProperty("configsources", "foo:123,bar:345,tcp/baz:333,quux");
        ConfigSourceSet set = ConfigSourceSet.createDefault();
        assertEquals(4, set.getSources().size());
        assertTrue(set.getSources().contains("tcp/foo:123"));
        assertTrue(set.getSources().contains("tcp/bar:345"));
        assertTrue(set.getSources().contains("tcp/baz:333"));
        assertTrue(set.getSources().contains("tcp/quux"));
    }

    @After
    public void cleanup() {
        System.clearProperty("configsources");
    }
}
