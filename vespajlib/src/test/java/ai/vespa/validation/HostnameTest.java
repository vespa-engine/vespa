// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
public class HostnameTest {

    @Test
    void testNames() {
        Hostname.of("name-123.0.321-eman");
        Hostname.of(("." + "a".repeat(32)).repeat(2).substring(1, 65));
        Hostname.of("123");

        assertThrows(IllegalArgumentException.class, () -> Hostname.of("_"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("-"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("."));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("-foo"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("foo-"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of(".foo"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("foo."));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("foo..bar"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("foo.-.bar"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("foo/"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("foo%"));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of(("." + "a".repeat(32)).repeat(2).substring(1, 66)));
        assertThrows(IllegalArgumentException.class, () -> Hostname.of("a".repeat(64)));
    }

}
