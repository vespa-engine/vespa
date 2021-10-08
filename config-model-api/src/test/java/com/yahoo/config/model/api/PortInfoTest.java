// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

import java.util.Arrays;

public class PortInfoTest {
    @Test
    public void testEquals() {
        PortInfo a = new PortInfo(1234, Arrays.asList("foo"));
        PortInfo b = new PortInfo(1234, Arrays.asList("foo"));
        PortInfo c = new PortInfo(1234, Arrays.asList("foo", "bar"));
        PortInfo d = new PortInfo(12345, Arrays.asList("foo"));
        new EqualsTester()
                .addEqualityGroup(a, b)
                .addEqualityGroup(c)
                .addEqualityGroup(d).testEquals();
    }
}
