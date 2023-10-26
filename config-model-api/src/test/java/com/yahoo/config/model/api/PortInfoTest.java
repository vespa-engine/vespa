// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PortInfoTest {
    @Test
    public void testEquals() {
        PortInfo a = new PortInfo(1234, Arrays.asList("foo"));
        PortInfo b = new PortInfo(1234, Arrays.asList("foo"));
        PortInfo c = new PortInfo(1234, Arrays.asList("foo", "bar"));
        PortInfo d = new PortInfo(12345, Arrays.asList("foo"));
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(c, d);
    }
}
