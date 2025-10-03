// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.bouncycastle.oer.its.ieee1609dot2.basetypes.Hostname;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
public class HostNameTest {

    @Test
    void testNames() {
        HostName.of("name-123.0.321-eman");
        HostName.of(("." + "a".repeat(32)).repeat(2).substring(1, 65));
        HostName.of("123");

        assertThrows(IllegalArgumentException.class, () -> HostName.of("_"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("-"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("."));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("-foo"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("foo-"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of(".foo"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("foo."));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("foo..bar"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("foo.-.bar"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("foo/"));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("foo%"));
        HostName.of("a".repeat(63));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("a".repeat(64)));

        int r = 8;
        String longHostname = ("." + "a".repeat(31)).repeat(r).substring(1, 32 * r);
        assertEquals(255, longHostname.length());
        HostName.of(longHostname);
        assertThrows(IllegalArgumentException.class, () -> HostName.of(longHostname + "z"));
    }

}

