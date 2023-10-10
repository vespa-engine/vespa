// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


public class ServiceInfoTest {

    @Test
    public void testEquals() {
        String commonConfigId = "common-config-id";
        String commonHostName = "common-host";

        ServiceInfo a = new ServiceInfo("0", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "bar"), commonConfigId, commonHostName);
        ServiceInfo b = new ServiceInfo("0", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "bar"), commonConfigId, commonHostName);
        ServiceInfo c = new ServiceInfo("0", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "baz"), commonConfigId, commonHostName);
        ServiceInfo d = new ServiceInfo("0", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("bar", "bar"), commonConfigId, commonHostName);
        ServiceInfo e = new ServiceInfo("0", "1", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "bar"), commonConfigId, commonHostName);
        ServiceInfo f = new ServiceInfo("1", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "bar"), commonConfigId, commonHostName);
        ServiceInfo g = new ServiceInfo("1", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "bar"), "different-config-id", commonHostName);
        ServiceInfo h = new ServiceInfo("1", "0", Arrays.asList(new PortInfo(33, null)), Collections.singletonMap("foo", "bar"), commonConfigId, "different-host");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, e);
        assertNotEquals(a, f);
        assertNotEquals(a, g);
        assertNotEquals(a, h);

        assertNotEquals(c, d);
        assertNotEquals(c, e);
        assertNotEquals(c, f);
        assertNotEquals(c, g);
        assertNotEquals(c, h);

        assertNotEquals(d, e);
        assertNotEquals(d, f);
        assertNotEquals(d, g);
        assertNotEquals(d, h);

        assertNotEquals(e, f);
        assertNotEquals(e, g);
        assertNotEquals(e, h);

        assertNotEquals(f, g);
        assertNotEquals(f, h);

        assertNotEquals(g, h);
    }
}
