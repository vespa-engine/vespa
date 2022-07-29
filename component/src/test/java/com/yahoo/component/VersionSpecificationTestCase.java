// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class VersionSpecificationTestCase {

    @Test
    void testPrimitiveCreation() {
        VersionSpecification version = new VersionSpecification(1, 2, 3, "qualifier");
        assertEquals(1, (int) version.getSpecifiedMajor());
        assertEquals(2, (int) version.getSpecifiedMinor());
        assertEquals(3, (int) version.getSpecifiedMicro());
        assertEquals("qualifier", version.getSpecifiedQualifier());
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals("qualifier", version.getQualifier());
    }

    @Test
    void testUnderspecifiedPrimitiveCreation() {
        VersionSpecification version = new VersionSpecification(1);
        assertEquals(1, (int) version.getSpecifiedMajor());
        assertNull(version.getSpecifiedMinor());
        assertNull(version.getSpecifiedMicro());
        assertNull(version.getSpecifiedQualifier());
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("", version.getQualifier());
    }

    @Test
    void testStringCreation() {
        VersionSpecification version = new VersionSpecification("1.2.3.qualifier");
        assertEquals(1, (int) version.getSpecifiedMajor());
        assertEquals(2, (int) version.getSpecifiedMinor());
        assertEquals(3, (int) version.getSpecifiedMicro());
        assertEquals("qualifier", version.getSpecifiedQualifier());
    }

    @Test
    void testUnderspecifiedStringCreation() {
        VersionSpecification version = new VersionSpecification("1");
        assertEquals(1, (int) version.getSpecifiedMajor());
        assertNull(version.getSpecifiedMinor());
        assertNull(version.getSpecifiedMicro());
        assertNull(version.getSpecifiedQualifier());
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("", version.getQualifier());
    }

    @Test
    void testEquality() {
        assertEquals(new VersionSpecification(), VersionSpecification.emptyVersionSpecification);
        assertEquals(new VersionSpecification(), new VersionSpecification(""));
        assertEquals(new VersionSpecification(1), new VersionSpecification("1"));
        assertEquals(new VersionSpecification(1, 2), new VersionSpecification("1.2"));
        assertEquals(new VersionSpecification(1, 2, 3), new VersionSpecification("1.2.3"));
        assertEquals(new VersionSpecification(1, 2, 3, "qualifier"), new VersionSpecification("1.2.3.qualifier"));
    }

    @Test
    void testToString() {
        assertEquals("", new VersionSpecification().toString());
        assertEquals("1", new VersionSpecification(1).toString());
        assertEquals("1.2", new VersionSpecification(1, 2).toString());
        assertEquals("1.2.3", new VersionSpecification(1, 2, 3).toString());
        assertEquals("1.2.3.qualifier", new VersionSpecification(1, 2, 3, "qualifier").toString());
    }

    @Test
    void testMatches() {
        assertTrue(new VersionSpecification("").matches(new Version("1")));
        assertTrue(new VersionSpecification("1").matches(new Version("1")));
        assertFalse(new VersionSpecification("1").matches(new Version("2")));
        assertTrue(new VersionSpecification("").matches(new Version("1.2.3")));
        assertFalse(new VersionSpecification("").matches(new Version("1.2.3.qualifier"))); // qualifier requires exact match

        assertTrue(new VersionSpecification("1.2").matches(new Version("1.2")));
        assertTrue(new VersionSpecification("1").matches(new Version("1.2")));
        assertFalse(new VersionSpecification("1.2").matches(new Version("1.3")));
        assertFalse(new VersionSpecification("1.2").matches(new Version("2")));

        assertTrue(new VersionSpecification("1.2.3").matches(new Version("1.2.3")));
        assertTrue(new VersionSpecification("1.2").matches(new Version("1.2.3")));
        assertTrue(new VersionSpecification("1").matches(new Version("1.2.3")));
        assertFalse(new VersionSpecification("1.2.3").matches(new Version("1.2.4")));
        assertFalse(new VersionSpecification("1.3").matches(new Version("1.2.3")));
        assertFalse(new VersionSpecification("2").matches(new Version("1.2.3")));

        assertTrue(new VersionSpecification("1.2.3.qualifier").matches(new Version("1.2.3.qualifier")));
        assertFalse(new VersionSpecification("1.2.3.qualifier1").matches(new Version("1.2.3.qualifier2")));
        assertFalse(new VersionSpecification("1.2.3.qualifier").matches(new Version("1.2.3")));
        assertFalse(new VersionSpecification("1.2.3.qualifier").matches(new Version("1.2")));
        assertFalse(new VersionSpecification("1.2.3.qualifier").matches(new Version("1")));
        assertFalse(new VersionSpecification("1.2.3.qualifier").matches(new Version("")));

        assertFalse(new VersionSpecification(1, null, null, null).matches(new Version("1.2.3.qualifier")));
        assertFalse(new VersionSpecification(1, 2, 0, "qualifier").matches(new Version("1.2.3.qualifier")));
        assertFalse(new VersionSpecification(1, 2, 3).matches(new Version("1.2.3.qualifier")));
    }

    @Test
    void testOrder() {
        assertEquals(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.2.3")), 0);
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.2.4")) < 0);
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.2.2")) > 0);

        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("2")) < 0);
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.3")) < 0);

        assertEquals(new VersionSpecification("1.0.0").compareTo(new VersionSpecification("1")), 0);
    }

    @Test
    void testValidIntersect() {
        VersionSpecification mostSpecific = new VersionSpecification(4, 2, 1);
        VersionSpecification leastSpecific = new VersionSpecification(4, 2);

        assertEquals(mostSpecific,
                mostSpecific.intersect(leastSpecific));
        assertEquals(mostSpecific,
                leastSpecific.intersect(mostSpecific));
    }

    @Test
    void testInvalidIntersect() {
        assertThrows(RuntimeException.class, () -> {
            new VersionSpecification(4, 1).intersect(
                    new VersionSpecification(4, 2));
        });
    }
}
