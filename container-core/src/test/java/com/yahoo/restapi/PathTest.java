// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class PathTest {

    @Test
    void testPath() {
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
    void testPathWithRest() {
        {
            Path path = new Path(URI.create("/a/1/bar/fuz/"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals(List.of(), path.getRest().segments());
        }

        {
            Path path = new Path(URI.create("/a/1/bar/fuz/kanoo"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals(List.of("kanoo"), path.getRest().segments());
        }

        {
            Path path = new Path(URI.create("/a/1/bar/fuz/kanoo/trips"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals(List.of("kanoo", "trips"), path.getRest().segments());
        }

        {
            Path path = new Path(URI.create("/a/1/bar/fuz/kanoo/trips/"));
            assertTrue(path.matches("/a/{foo}/bar/{b}/{*}"));
            assertEquals("1", path.get("foo"));
            assertEquals("fuz", path.get("b"));
            assertEquals(List.of("kanoo", "trips"), path.getRest().segments());
        }
    }

    @Test
    void testUrlEncodedPath() {
        assertTrue(new Path(URI.create("/a/%62/c")).matches("/a/b/c"));
        assertFalse(new Path(URI.create("/a/b%2fc"), __ -> {
        }).matches("/a/b/c"));
        assertEquals("path segments cannot be \"\", \".\", or \"..\", but got: '..'",
                assertThrows(IllegalArgumentException.class,
                        () -> new Path(URI.create("/foo")).matches("/foo/bar/%2e%2e")).getMessage());

        Path path = new Path(URI.create("/%61/%2f/%63"), __ -> {
        });
        assertTrue(path.matches("/a/{slash}/{c}"));
        assertEquals("/", path.get("slash"));
        assertEquals("c", path.get("c"));
    }

    @Test
    void testInvalidPaths() {
        assertInvalid(URI.create("/foo/../bar"));
        assertInvalid(URI.create("/foo/%2e%2e/bar"));
        assertInvalidPathSpec(URI.create("/foo/bar"), "/foo/bar/..");
        assertInvalidPathSpec(URI.create("/foo/bar"), "/foo/../bar");
    }

    private void assertInvalid(URI uri) {
        try {
            new Path(uri);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void assertInvalidPathSpec(URI uri, String pathSpec) {
        try {
            Path path = new Path(uri);
            path.matches(pathSpec);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
        }
    }

}
