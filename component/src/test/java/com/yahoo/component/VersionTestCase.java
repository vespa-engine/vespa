// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class VersionTestCase {

    @Test
    void testPrimitiveCreation() {
        Version version = new Version(1, 2, 3, "qualifier");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals("qualifier", version.getQualifier());
    }

    @Test
    void testUnderspecifiedPrimitiveCreation() {
        Version version = new Version(1);
        assertEquals(1, version.getMajor());
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("", version.getQualifier());
    }

    @Test
    void testStringCreation() {
        Version version = new Version("1.2.3.qualifier");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals("qualifier", version.getQualifier());
    }

    @Test
    void testUtf8StringCreation() {
        Version version = new Version((Utf8Array) new Utf8String("1.2.3.qualifier"));
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals("qualifier", version.getQualifier());
    }

    @Test
    void testUnderspecifiedStringCreation() {
        Version version = new Version("1");
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("", version.getQualifier());
    }

    @Test
    void testEquality() {
        assertEquals(new Version(), Version.emptyVersion);
        assertEquals(new Version(), new Version(""));
        assertEquals(new Version(0, 0, 0), Version.emptyVersion);
        assertEquals(new Version(1), new Version("1"));
        assertEquals(new Version(1, 2), new Version("1.2"));
        assertEquals(new Version(1, 2, 3), new Version("1.2.3"));
        assertEquals(new Version(1, 2, 3, "qualifier"), new Version("1.2.3.qualifier"));
    }

    @Test
    void testToString() {
        assertEquals("", new Version().toString());
        assertEquals("1", new Version(1).toString());
        assertEquals("1.2", new Version(1, 2).toString());
        assertEquals("1.2.3", new Version(1, 2, 3).toString());
        assertEquals("1.2.3.qualifier", new Version(1, 2, 3, "qualifier").toString());
    }

    @Test
    void testToFullString() {
        assertEquals("0.0.0", new Version().toFullString());
        assertEquals("1.0.0", new Version(1).toFullString());
        assertEquals("1.2.0", new Version(1, 2).toFullString());
        assertEquals("1.2.3", new Version(1, 2, 3).toFullString());
        assertEquals("1.2.3.qualifier", new Version(1, 2, 3, "qualifier").toFullString());
    }

    @Test
    void testOrder() {
        assertEquals(new Version("1.2.3").compareTo(new Version("1.2.3")), 0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.2.4")) < 0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.2.3.foo")) < 0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.2.2")) > 0);
        assertTrue(new Version("1.2.3.foo").compareTo(new Version("1.2.3")) > 0);
        assertTrue(new Version("1.2.3").compareTo(new Version("2")) < 0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.3")) < 0);
        assertEquals(new Version("1.0.0").compareTo(new Version("1")), 0);
    }

    @Test
    void testIsBefore() {
        assertFalse(new Version("1.2.3").isBefore(new Version("0.2.3")));
        assertFalse(new Version("1.2.3").isBefore(new Version("1.1.3")));
        assertFalse(new Version("1.2.3").isBefore(new Version("1.2.2")));
        assertFalse(new Version("1.2.3").isBefore(new Version("1.2.3")));
        assertFalse(new Version("1.2.3.foo").isBefore(new Version("1.2.3")));
        assertTrue(new Version("1.2.3").isBefore(new Version("1.2.4")));
        assertTrue(new Version("1.2.3").isBefore(new Version("1.3.3")));
        assertTrue(new Version("1.2.3").isBefore(new Version("2.2.3")));
        assertTrue(new Version("1.2.3").isBefore(new Version("1.2.3.foo")));
    }

    @Test
    void testIsAfter() {
        assertTrue(new Version("1.2.3").isAfter(new Version("0.2.3")));
        assertTrue(new Version("1.2.3").isAfter(new Version("1.1.3")));
        assertTrue(new Version("1.2.3").isAfter(new Version("1.2.2")));
        assertTrue(new Version("1.2.3.foo").isAfter(new Version("1.2.3")));
        assertFalse(new Version("1.2.3").isAfter(new Version("1.2.3")));
        assertFalse(new Version("1.2.3").isAfter(new Version("1.2.4")));
        assertFalse(new Version("1.2.3").isAfter(new Version("1.3.3")));
        assertFalse(new Version("1.2.3").isAfter(new Version("2.2.3")));
        assertFalse(new Version("1.2.3").isAfter(new Version("1.2.3.foo")));
    }

}
