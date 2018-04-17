// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * tests creating invalid names
 *
 * @author bratseth
 */
public class NameTestCase {

    @Test
    public void testNames() {
        assertLegalName("aB");
        assertIllegalName("a.");
        assertLegalName("_a_b");
        assertLegalName("a_b");
        assertLegalName("a/b");
        assertLegalName("/a/b");
        assertLegalName("/a/b/");
        assertIllegalName("");
    }

    @Test
    public void testFieldNames() {
        assertLegalFieldName("aB");
        try {
            QueryProfile profile=new QueryProfile("test");
            profile.set("a.","anyValue", (QueryProfileRegistry)null);
            fail("Should have failed");
        } catch (IllegalArgumentException e) {
            assertEquals("'a.' is not a legal compound name. Names can not end with a dot.", e.getMessage());
        }
        assertLegalFieldName("_a_b");
        assertLegalFieldName("a_b");
        assertLegalFieldName("a/b");
        assertLegalFieldName("/a/b");
        assertLegalFieldName("/a/b/");
        assertIllegalFieldName("");
        assertIllegalFieldName("aBc.dooEee.ce_d.-some-other.moreHere",
                               "Could not set 'aBc.dooEee.ce_d.-some-other.moreHere' to 'anyValue'",
                               "Illegal name '-some-other'");
    }

    private void assertLegalName(String name) {
        new QueryProfile(name);
        new QueryProfileType(name);
    }

    private void assertLegalFieldName(String name) {
        new QueryProfile(name).set(name, "value", (QueryProfileRegistry)null);
        new FieldDescription(name,FieldType.stringType);
    }

    /** Checks that this is illegal both for profiles and types */
    private void assertIllegalName(String name) {
        try {
            new QueryProfile(name);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            if (!name.isEmpty())
                assertEquals("Illegal name '" + name + "'",e.getMessage());
        }

        try {
            new QueryProfileType(name);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            if (!name.isEmpty())
                assertEquals("Illegal name '" + name + "'",e.getMessage());
        }
    }

    private void assertIllegalFieldName(String name) {
        assertIllegalFieldName(name,"Could not set '" + name + "' to 'anyValue'","Illegal name '" + name + "'");
    }

    /** Checks that this is illegal both for profiles and types */
    private void assertIllegalFieldName(String name, String expectedHighError, String expectedLowError) {
        try {
            QueryProfile profile=new QueryProfile("test");
            profile.set(name, "anyValue", (QueryProfileRegistry)null);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals(expectedHighError + ": " + expectedLowError, Exceptions.toMessageString(e));
        }

        try {
            new FieldDescription(name, FieldType.stringType);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals(expectedLowError, e.getMessage());
        }
    }

}
