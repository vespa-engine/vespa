// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 */
public class VersionCompatibilityTest {

    @Test
    void testNoIncompatibilities() {
        List<String> versions = List.of();
        VersionCompatibility compatibility = VersionCompatibility.fromVersionList(versions);
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 0, 0)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 0, 1)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 1, 0)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(1, 0, 0)));
    }

    @Test
    void testValidIncompatibilities() {
        List<String> versions = List.of("1.2.*", "2", "3.*", "4.0.0", "4.0.1", "4.0.3", "4.1.0", "5");
        VersionCompatibility compatibility = VersionCompatibility.fromVersionList(versions);
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 0, 0)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(1, 1, 1)));
        assertFalse(compatibility.accept(new Version(0, 0, 0), new Version(1, 2, 3)));
        assertFalse(compatibility.accept(new Version(1, 1, 0), new Version(1, 2, 0)));
        assertFalse(compatibility.accept(new Version(1, 2, 1), new Version(1, 2, 0)));
        assertFalse(compatibility.accept(new Version(1, 1, 0), new Version(1, 3, 0)));
        assertTrue(compatibility.accept(new Version(1, 2, 3), new Version(1, 2, 3)));
        assertTrue(compatibility.accept(new Version(1, 3, 0), new Version(1, 9, 9)));
        assertFalse(compatibility.accept(new Version(1, 3, 0), new Version(2, 0, 0)));
        assertTrue(compatibility.accept(new Version(2, 0, 0), new Version(2, 2, 2)));
        assertFalse(compatibility.accept(new Version(2, 0, 0), new Version(3, 0, 0)));
        assertTrue(compatibility.accept(new Version(3, 0, 0), new Version(3, 0, 0)));
        assertFalse(compatibility.accept(new Version(3, 0, 0), new Version(3, 1, 0)));
        assertTrue(compatibility.accept(new Version(3, 0, 0), new Version(3, 0, 1)));
        assertFalse(compatibility.accept(new Version(3, 0, 0), new Version(4, 0, 0)));
        assertFalse(compatibility.accept(new Version(4, 0, 0), new Version(4, 0, 1)));
        assertTrue(compatibility.accept(new Version(4, 0, 1), new Version(4, 0, 2)));
        assertFalse(compatibility.accept(new Version(4, 0, 2), new Version(4, 0, 3)));
        assertFalse(compatibility.accept(new Version(4, 0, 3), new Version(4, 1, 0)));
        assertFalse(compatibility.accept(new Version(4, 1, 0), new Version(5, 0, 0)));
        assertTrue(compatibility.accept(new Version(5, 0, 0), new Version(6, 0, 0)));
        assertFalse(compatibility.accept(new Version(0, 0, 0), new Version(2, 0, 0)));
        assertFalse(compatibility.accept(new Version(0, 0, 0), new Version(6, 0, 0)));
    }

    @Test
    void testIllegalIncompatibilities() {
        assertThrows(List.of("1", "*"),         IllegalArgumentException.class, "may not have siblings");
        assertThrows(List.of("*", "*.*"),       IllegalArgumentException.class, "may not have siblings");
        assertThrows(List.of("*", "*"),         IllegalArgumentException.class, "may not have siblings");
        assertThrows(List.of("*.1"),            IllegalArgumentException.class, "may only have wildcard children");
        assertThrows(List.of("0", "0"),         IllegalArgumentException.class, "duplicate element");
        assertThrows(List.of("0", "0.0.0"),     IllegalArgumentException.class, "duplicate element");
        assertThrows(List.of("0.0.0", "0.0.0"), IllegalArgumentException.class, "duplicate element");
        assertThrows(List.of("."),              IllegalArgumentException.class, "1 to 3 parts");
        assertThrows(List.of("0.0.0.0"),        IllegalArgumentException.class, "1 to 3 parts");
        assertThrows(List.of("-1"),             IllegalArgumentException.class, "must be non-negative");
        assertThrows(List.of(""),               NumberFormatException.class,    "input string: \"\"");
        assertThrows(List.of("x"),              NumberFormatException.class,    "input string: \"x\"");
    }

    static void assertThrows(List<String> spec, Class<? extends IllegalArgumentException> clazz, String fragment) {
        IllegalArgumentException thrown = null;
        try {
            VersionCompatibility.fromVersionList(spec);
        }
        catch (IllegalArgumentException e) {
            if (clazz.isInstance(e) && e.getMessage().toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT)))
                return;
            thrown = e;
        }
        fail("Should fail with " + clazz.getSimpleName() + " containing '" + fragment + "' in its message, but got " +
             (thrown == null ? "no exception" : "'" + thrown + "'"));
    }

}
