// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class PathTest {
    @Test
    public void testGetName() {
        assertEquals("baz", getAbsolutePath().getName());
        assertEquals("baz", getRelativePath().getName());
        assertEquals("baz", getWithSlashes().getName());
        assertEquals("baz", getAppended().getName());
        assertEquals("foo", getOne().getName());
    }

    @Test
    public void testEquals() {
        assertEquals(getAbsolutePath(), getAbsolutePath());
        assertEquals(getAbsolutePath(), getRelativePath());
        assertEquals(getAbsolutePath(), getWithSlashes());
        assertEquals(getAbsolutePath(), getAppended());
        assertNotEquals(getAbsolutePath(), getOne());

        assertEquals(getRelativePath(), getAbsolutePath());
        assertEquals(getRelativePath(), getRelativePath());
        assertEquals(getRelativePath(), getWithSlashes());
        assertEquals(getRelativePath(), getAppended());
        assertNotEquals(getRelativePath(), getOne());

        assertEquals(getWithSlashes(), getAbsolutePath());
        assertEquals(getWithSlashes(), getRelativePath());
        assertEquals(getWithSlashes(), getWithSlashes());
        assertEquals(getWithSlashes(), getAppended());
        assertNotEquals(getWithSlashes(), getOne());

        assertEquals(getAppended(), getAbsolutePath());
        assertEquals(getAppended(), getRelativePath());
        assertEquals(getAppended(), getWithSlashes());
        assertEquals(getAppended(), getAppended());
        assertNotEquals(getAppended(), getOne());

        assertNotEquals(getOne(), getAbsolutePath());
        assertNotEquals(getOne(), getRelativePath());
        assertNotEquals(getOne(), getWithSlashes());
        assertNotEquals(getOne(), getAppended());
        assertEquals(getOne(), getOne());
    }

    @Test
    public void testGetPath() {
        assertEquals("foo/bar/baz", getAbsolutePath().getRelative());
        assertEquals("foo/bar/baz", getRelativePath().getRelative());
        assertEquals("foo/bar/baz", getWithSlashes().getRelative());
        assertEquals("foo/bar/baz", getAppended().getRelative());
        assertEquals("foo", getOne().getRelative());
    }

    @Test
    public void testGetParentPath() {
        assertEquals("foo/bar", getAbsolutePath().getParentPath().getRelative());
        assertEquals("foo/bar", getRelativePath().getParentPath().getRelative());
        assertEquals("foo/bar", getWithSlashes().getParentPath().getRelative());
        assertEquals("foo/bar", getAppended().getParentPath().getRelative());
        assertTrue(getOne().getParentPath().getRelative().isEmpty());
    }

    @Test
    public void testGetAbsolutePath() {
        assertEquals("/foo/bar/baz", getAbsolutePath().getAbsolute());
        assertEquals("/foo/bar", getAbsolutePath().getParentPath().getAbsolute());
    }

    @Test
    public void testEmptyPath() {
        assertTrue(Path.createRoot().getName().isEmpty());
        assertTrue(Path.createRoot().getRelative().isEmpty());
        assertTrue(Path.createRoot().getParentPath().getRelative().isEmpty());
        assertTrue(Path.createRoot().isRoot());
    }

    @Test
    public void testDelimiters() {
        assertEquals("foo/bar", Path.fromString("foo/bar", ",").getName());
        assertEquals("bar", Path.fromString("foo/bar", "/").getName());
        assertEquals("foo,bar", Path.fromString("foo,bar", "/").getName());
        assertEquals("bar", Path.fromString("foo,bar", ",").getName());
        assertEquals("foo,bar", Path.createRoot(",").append("foo").append("bar").getRelative());
    }

    @Test
    public void testAppendPath() {
        Path p1 = getAbsolutePath();
        Path p2 = getAbsolutePath();
        Path p3 = p1.append(p2);
        assertEquals("/foo/bar/baz", p1.getAbsolute());
        assertEquals("/foo/bar/baz", p2.getAbsolute());
        assertEquals("/foo/bar/baz/foo/bar/baz", p3.getAbsolute());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDoubleDot() {
        Path.fromString("foo/../bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLastWithDelimiter() {
        Path.fromString("foo/bar").withLast("../../baz");
    }

    private Path getRelativePath() {
        return Path.fromString("foo/bar/baz");
    }

    private Path getAbsolutePath() {
        return Path.fromString("/foo/bar/baz");
    }

    private Path getWithSlashes() {
        return Path.fromString("/foo//bar///baz/");
    }

    private Path getAppended() {
        return Path.createRoot().append("foo").append("bar").append("baz");
    }

    private Path getOne() {
        return Path.fromString("foo");
    }
}
