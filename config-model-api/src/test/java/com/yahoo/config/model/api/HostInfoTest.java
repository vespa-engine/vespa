// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import org.junit.Test;
import com.google.common.testing.EqualsTester;

import java.util.Arrays;

public class HostInfoTest {
    @Test
    public void testEquals() {
        HostInfo a = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("foo", "bar", null, null, "config-id", "host-name")));
        HostInfo b = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("foo", "bar", null, null, "config-id", "host-name")));
        HostInfo c = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("foo", "baz", null, null, "config-id", "host-name")));
        HostInfo d = new HostInfo("foo.yahoo.com", Arrays.asList(new ServiceInfo("bar", "baz", null, null, "config-id", "host-name")));
        HostInfo e = new HostInfo("bar.yahoo.com", null);
        new EqualsTester()
                .addEqualityGroup(a, b)
                .addEqualityGroup(c)
                .addEqualityGroup(d)
                .addEqualityGroup(e).testEquals();
    }
}
