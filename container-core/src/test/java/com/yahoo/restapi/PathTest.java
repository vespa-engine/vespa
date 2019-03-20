// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class PathTest {

    @Test
    public void testWithPrefix() {
        // Test that a path with a prefix matches spec without the prefix
        Path path = new Path("/ball/a/1/bar/fuz", "/ball");
        assertTrue(path.matches("/a/{foo}/bar/{b}"));
        assertEquals("1", path.get("foo"));
        assertEquals("fuz", path.get("b"));

        // Also test that prefix does not cause false matches
        assertFalse(path.matches("/ball/a/{foo}/zoo/{b}"));
    }


    @Test
    public void testPath() {
        assertFalse(new Path("").matches("/a/{foo}/bar/{b}"));
        assertFalse(new Path("///").matches("/a/{foo}/bar/{b}"));
        assertFalse(new Path("///foo").matches("/a/{foo}/bar/{b}"));
        assertFalse(new Path("///bar/").matches("/a/{foo}/bar/{b}"));
        Path path = new Path("/a/1/bar/fuz");
        assertTrue(path.matches("/a/{foo}/bar/{b}"));
        assertEquals("1", path.get("foo"));
        assertEquals("fuz", path.get("b"));
    }

    @Test
    public void testPathWithRest() {
        {
            Path path = new Path("/a/1/bar/fuz/");
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("", path.getRest());
        }

        {
            Path path = new Path("/a/1/bar/fuz/kanoo");
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("kanoo", path.getRest());
        }

        {
            Path path = new Path("/a/1/bar/fuz/kanoo/trips");
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("kanoo/trips", path.getRest());
        }

        {
            Path path = new Path("/a/1/bar/fuz/kanoo/trips/");
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("kanoo/trips/", path.getRest());
        }
    }

}
