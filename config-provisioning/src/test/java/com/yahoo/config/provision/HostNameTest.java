// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

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
        assertThrows(IllegalArgumentException.class, () -> HostName.of(("." + "a".repeat(32)).repeat(2).substring(1, 66)));
        assertThrows(IllegalArgumentException.class, () -> HostName.of("a".repeat(64)));
    }

}

