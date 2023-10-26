// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import org.junit.Test;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class HostInfoTest {
    @Test
    public void testEquals() {
        HostInfo a = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("foo", "bar", null, null, "config-id", "host-name")));
        HostInfo b = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("foo", "bar", null, null, "config-id", "host-name")));
        HostInfo c = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("foo", "baz", null, null, "config-id", "host-name")));
        HostInfo d = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("bar", "baz", null, null, "config-id", "host-name")));
        HostInfo e = new HostInfo("bar.yahoo.com", null);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, d);
        assertNotEquals(c, d);
        assertNotEquals(c, e);
        assertNotEquals(d, e);
    }
}
