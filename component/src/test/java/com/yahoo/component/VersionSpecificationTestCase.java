// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class VersionSpecificationTestCase {

    @Test
    public void testPrimitiveCreation() {
        VersionSpecification version=new VersionSpecification(1,2,3,"qualifier");
        assertEquals(1, (int)version.getSpecifiedMajor());
        assertEquals(2, (int)version.getSpecifiedMinor());
        assertEquals(3, (int)version.getSpecifiedMicro());
        assertEquals("qualifier",version.getSpecifiedQualifier());
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals("qualifier",version.getQualifier());
    }

    @Test
    public void testUnderspecifiedPrimitiveCreation() {
        VersionSpecification version=new VersionSpecification(1);
        assertEquals(1,(int)version.getSpecifiedMajor());
        assertEquals(null,version.getSpecifiedMinor());
        assertEquals(null,version.getSpecifiedMicro());
        assertEquals(null,version.getSpecifiedQualifier());
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("",version.getQualifier());
    }

    @Test
    public void testStringCreation() {
        VersionSpecification version=new VersionSpecification("1.2.3.qualifier");
        assertEquals(1,(int)version.getSpecifiedMajor());
        assertEquals(2,(int)version.getSpecifiedMinor());
        assertEquals(3,(int)version.getSpecifiedMicro());
        assertEquals("qualifier",version.getSpecifiedQualifier());
    }

    @Test
    public void testUnderspecifiedStringCreation() {
        VersionSpecification version=new VersionSpecification("1");
        assertEquals(1,(int)version.getSpecifiedMajor());
        assertEquals(null,version.getSpecifiedMinor());
        assertEquals(null,version.getSpecifiedMicro());
        assertEquals(null,version.getSpecifiedQualifier());
        assertEquals(1, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("",version.getQualifier());
    }

    @Test
    public void testEquality() {
        assertEquals(new VersionSpecification(),VersionSpecification.emptyVersionSpecification);
        assertEquals(new VersionSpecification(),new VersionSpecification(""));
        assertEquals(new VersionSpecification(1),new VersionSpecification("1"));
        assertEquals(new VersionSpecification(1,2),new VersionSpecification("1.2"));
        assertEquals(new VersionSpecification(1,2,3),new VersionSpecification("1.2.3"));
        assertEquals(new VersionSpecification(1,2,3,"qualifier"),new VersionSpecification("1.2.3.qualifier"));
    }

    @Test
    public void testToString() {
        assertEquals("",new VersionSpecification().toString());
        assertEquals("1",new VersionSpecification(1).toString());
        assertEquals("1.2",new VersionSpecification(1,2).toString());
        assertEquals("1.2.3",new VersionSpecification(1,2,3).toString());
        assertEquals("1.2.3.qualifier",new VersionSpecification(1,2,3,"qualifier").toString());
    }

    @Test
    public void testMatches() {
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
    public void testOrder() {
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.2.3"))==0);
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.2.4"))<0);
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.2.2"))>0);

        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("2"))<0);
        assertTrue(new VersionSpecification("1.2.3").compareTo(new VersionSpecification("1.3"))<0);

        assertTrue(new VersionSpecification("1.0.0").compareTo(new VersionSpecification("1"))==0);
    }

    @Test
    public void testValidIntersect() {
        VersionSpecification mostSpecific = new VersionSpecification(4, 2, 1);
        VersionSpecification leastSpecific = new VersionSpecification(4, 2);

        assertEquals(mostSpecific,
                mostSpecific.intersect(leastSpecific));
        assertEquals(mostSpecific,
                leastSpecific.intersect(mostSpecific));
    }

    @Test(expected=RuntimeException.class)
    public void testInvalidIntersect() {
        new VersionSpecification(4, 1).intersect(
                new VersionSpecification(4, 2));
    }
}
