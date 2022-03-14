// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author jonmv
 */
public class VersionIncompatibilityTest {

    @Test
    public void testNoIncompatibilities() {
        List<String> versions = List.of();
        VersionCompatibility compatibility = VersionCompatibility.fromVersionList(versions);
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 0, 0)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 0, 1)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(0, 1, 0)));
        assertTrue(compatibility.accept(new Version(0, 0, 0), new Version(1, 0, 0)));
    }

    @Test
    public void testValidIncompatibilities() {
        List<String> versions = List.of("1.2.*", "2", "3.*", "4.0.0", "4.0.1", "4.0.3", "4.1.0", "5");
        VersionCompatibility compatibility = VersionCompatibility.fromVersionList(versions);
        assertTrue (compatibility.accept(new Version(0, 0, 0), new Version(0, 0, 0)));
        assertTrue (compatibility.accept(new Version(0, 0, 0), new Version(1, 1, 1)));
        assertFalse(compatibility.accept(new Version(0, 0, 0), new Version(1, 2, 3)));
        assertFalse(compatibility.accept(new Version(1, 1, 0), new Version(1, 2, 0)));
        assertFalse(compatibility.accept(new Version(1, 2, 1), new Version(1, 2, 0)));
        assertFalse(compatibility.accept(new Version(1, 1, 0), new Version(1, 3, 0)));
        assertTrue (compatibility.accept(new Version(1, 2, 3), new Version(1, 2, 3)));
        assertTrue (compatibility.accept(new Version(1, 3, 0), new Version(1, 9, 9)));
        assertFalse(compatibility.accept(new Version(1, 3, 0), new Version(2, 0, 0)));
        assertTrue (compatibility.accept(new Version(2, 0, 0), new Version(2, 2, 2)));
        assertFalse(compatibility.accept(new Version(2, 0, 0), new Version(3, 0, 0)));
        assertTrue (compatibility.accept(new Version(3, 0, 0), new Version(3, 0, 0)));
        assertFalse(compatibility.accept(new Version(3, 0, 0), new Version(3, 1, 0)));
        assertTrue (compatibility.accept(new Version(3, 0, 0), new Version(3, 0, 1)));
        assertFalse(compatibility.accept(new Version(3, 0, 0), new Version(4, 0, 0)));
        assertFalse(compatibility.accept(new Version(4, 0, 0), new Version(4, 0, 1)));
        assertTrue (compatibility.accept(new Version(4, 0, 1), new Version(4, 0, 2)));
        assertFalse(compatibility.accept(new Version(4, 0, 2), new Version(4, 0, 3)));
        assertFalse(compatibility.accept(new Version(4, 0, 3), new Version(4, 1, 0)));
        assertFalse(compatibility.accept(new Version(4, 1, 0), new Version(5, 0, 0)));
        assertTrue (compatibility.accept(new Version(5, 0, 0), new Version(6, 0, 0)));
        assertFalse(compatibility.accept(new Version(0, 0, 0), new Version(2, 0, 0)));
        assertFalse(compatibility.accept(new Version(0, 0, 0), new Version(6, 0, 0)));
    }

    @Test
    public void testIllegalIncompatibilities() {
        try {
            VersionCompatibility.fromVersionList(List.of("1", "*"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("*", "*"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("-1"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("0", "0"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("0", "0.0.0"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("0.0.0", "0.0.0"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("*.1"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("*", "*.*"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of(""));
            fail();
        }
        catch (NumberFormatException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("0.0.0.0"));
            fail();
        }
        catch (IllegalArgumentException expected) { }
        try {
            VersionCompatibility.fromVersionList(List.of("x"));
            fail();
        }
        catch (NumberFormatException expected) { }
    }

}
