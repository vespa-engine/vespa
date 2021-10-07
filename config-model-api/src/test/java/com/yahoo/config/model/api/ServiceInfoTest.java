// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;


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

        new EqualsTester()
                .addEqualityGroup(a, b)
                .addEqualityGroup(c)
                .addEqualityGroup(d)
                .addEqualityGroup(e)
                .addEqualityGroup(f)
                .addEqualityGroup(g)
                .addEqualityGroup(h).testEquals();
    }
}
