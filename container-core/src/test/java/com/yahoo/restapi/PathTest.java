// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import org.junit.Test;

import java.net.URI;

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
        Path path = new Path(URI.create("/ball/a/1/bar/fuz"), "/ball");
        assertTrue(path.matches("/a/{foo}/bar/{b}"));
        assertEquals("1", path.get("foo"));
        assertEquals("fuz", path.get("b"));

        // Also test that prefix does not cause false matches
        assertFalse(path.matches("/ball/a/{foo}/zoo/{b}"));
    }


    @Test
    public void testPath() {
        assertFalse(new Path(URI.create("")).matches("/a/{foo}/bar/{b}"));
        assertFalse(new Path(URI.create("///")).matches("/a/{foo}/bar/{b}"));
        assertFalse(new Path(URI.create("///foo")).matches("/a/{foo}/bar/{b}"));
        assertFalse(new Path(URI.create("///bar/")).matches("/a/{foo}/bar/{b}"));
        Path path = new Path(URI.create("/a/1/bar/fuz"));
        assertTrue(path.matches("/a/{foo}/bar/{b}"));
        assertEquals("1", path.get("foo"));
        assertEquals("fuz", path.get("b"));
    }

    @Test
    public void testPathWithRest() {
        {
            Path path = new Path(URI.create("/a/1/bar/fuz/"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("", path.getRest());
        }

        {
            Path path = new Path(URI.create("/a/1/bar/fuz/kanoo"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("kanoo", path.getRest());
        }

        {
            Path path = new Path(URI.create("/a/1/bar/fuz/kanoo/trips"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("kanoo/trips", path.getRest());
        }

        {
            Path path = new Path(URI.create("/a/1/bar/fuz/kanoo/trips/"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals("kanoo/trips/", path.getRest());
        }
    }

    @Test
    public void testUrlEncodedPath() {
        assertTrue(new Path(URI.create("/a/%62/c")).matches("/a/b/c"));
        assertTrue(new Path(URI.create("/a/%2e%2e/c")).matches("/a/../c"));
        assertFalse(new Path(URI.create("/a/b%2fc")).matches("/a/b/c"));

        Path path = new Path(URI.create("/%61/%2f/%63"));
        assertTrue(path.matches("/a/{slash}/{c}"));
        assertEquals("/", path.get("slash"));
        assertEquals("c", path.get("c"));
    }

}
