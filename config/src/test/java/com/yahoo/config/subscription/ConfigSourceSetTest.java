// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import org.junit.Test;
import org.junit.After;

import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author <a href="gv@yahoo-inc.com">G. Voldengen</a>
 */
public class ConfigSourceSetTest {
    @Test
    public void testEquals() {
        assertTrue(new ConfigSourceSet().equals(new ConfigSourceSet()));
        assertFalse(new ConfigSourceSet().equals(new ConfigSourceSet(new String[]{"a"})));

        assertTrue(new ConfigSourceSet(new String[]{"a"}).equals(new ConfigSourceSet(new String[]{"a"})));
        assertTrue(new ConfigSourceSet(new String[]{"a"}).equals(new ConfigSourceSet(new String[]{"  A  "})));
        assertTrue(new ConfigSourceSet(new String[]{"a"}).equals(new ConfigSourceSet(new String[]{"A", "a"})));
        assertTrue(new ConfigSourceSet(new String[]{"A"}).equals(new ConfigSourceSet(new String[]{"a", " a  "})));

        assertFalse(new ConfigSourceSet(new String[]{"a"}).equals(new ConfigSourceSet(new String[]{"b"})));
        assertFalse(new ConfigSourceSet(new String[]{"a"}).equals(new ConfigSourceSet(new String[]{"a", "b"})));

        assertTrue(new ConfigSourceSet(new String[]{"a", "b"}).equals(new ConfigSourceSet(new String[]{"a", "b"})));
        assertTrue(new ConfigSourceSet(new String[]{"b", "a"}).equals(new ConfigSourceSet(new String[]{"a", "b"})));
        assertTrue(new ConfigSourceSet(new String[]{"A", " b"}).equals(new ConfigSourceSet(new String[]{"a ", "B"})));
        assertTrue(new ConfigSourceSet(new String[]{"b", "a", "c"})
                .equals(new ConfigSourceSet(new String[]{"a", "b", "c"})));

        assertFalse(new ConfigSourceSet(new String[]{"a", "b"}).equals(new ConfigSourceSet(new String[]{"b", "c"})));
        assertFalse(new ConfigSourceSet().equals("foo"));
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
        assertThat(set.getSources().size(), is(4));
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
