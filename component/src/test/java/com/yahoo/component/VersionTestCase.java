// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

/**
 * @author bratseth
 */
public class VersionTestCase extends junit.framework.TestCase {

    public void testPrimitiveCreation() {
        Version version=new Version(1,2,3,"qualifier");
        assertEquals(1,version.getMajor());
        assertEquals(2,version.getMinor());
        assertEquals(3,version.getMicro());
        assertEquals("qualifier",version.getQualifier());
    }

    public void testUnderspecifiedPrimitiveCreation() {
        Version version=new Version(1);
        assertEquals(1,version.getMajor());
        assertEquals(1,version.getMajor());
        assertEquals(0,version.getMinor());
        assertEquals(0,version.getMicro());
        assertEquals("",version.getQualifier());
    }

    public void testStringCreation() {
        Version version=new Version("1.2.3.qualifier");
        assertEquals(1,version.getMajor());
        assertEquals(2,version.getMinor());
        assertEquals(3,version.getMicro());
        assertEquals("qualifier",version.getQualifier());
    }
    public void testUtf8StringCreation() {
        Version version=new Version((Utf8Array)new Utf8String("1.2.3.qualifier"));
        assertEquals(1,version.getMajor());
        assertEquals(2,version.getMinor());
        assertEquals(3,version.getMicro());
        assertEquals("qualifier",version.getQualifier());
    }

    public void testUnderspecifiedStringCreation() {
        Version version=new Version("1");
        assertEquals(1,version.getMajor());
        assertEquals(0,version.getMinor());
        assertEquals(0,version.getMicro());
        assertEquals("",version.getQualifier());
    }

    public void testEquality() {
        assertEquals(new Version(),Version.emptyVersion);
        assertEquals(new Version(),new Version(""));
        assertEquals(new Version(0,0,0),Version.emptyVersion);
        assertEquals(new Version(1),new Version("1"));
        assertEquals(new Version(1,2),new Version("1.2"));
        assertEquals(new Version(1,2,3),new Version("1.2.3"));
        assertEquals(new Version(1,2,3,"qualifier"),new Version("1.2.3.qualifier"));
    }

    public void testToString() {
        assertEquals("",new Version().toString());
        assertEquals("1",new Version(1).toString());
        assertEquals("1.2",new Version(1,2).toString());
        assertEquals("1.2.3",new Version(1,2,3).toString());
        assertEquals("1.2.3.qualifier",new Version(1,2,3,"qualifier").toString());
    }

    public void testToFullString() {
        assertEquals("0.0.0",new Version().toFullString());
        assertEquals("1.0.0",new Version(1).toFullString());
        assertEquals("1.2.0",new Version(1,2).toFullString());
        assertEquals("1.2.3",new Version(1,2,3).toFullString());
        assertEquals("1.2.3.qualifier",new Version(1,2,3,"qualifier").toFullString());
    }

    public void testOrder() {
        assertTrue(new Version("1.2.3").compareTo(new Version("1.2.3"))==0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.2.4"))<0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.2.2"))>0);

        assertTrue(new Version("1.2.3").compareTo(new Version("2"))<0);
        assertTrue(new Version("1.2.3").compareTo(new Version("1.3"))<0);

        assertTrue(new Version("1.0.0").compareTo(new Version("1"))==0);
    }

}
