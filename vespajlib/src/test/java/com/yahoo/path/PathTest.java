// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.path;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class PathTest {
    @Test
    public void testGetName() {
        assertThat(getAbsolutePath().getName(), is("baz"));
        assertThat(getRelativePath().getName(), is("baz"));
        assertThat(getWithSlashes().getName(), is("baz"));
        assertThat(getAppended().getName(), is("baz"));
        assertThat(getOne().getName(), is("foo"));
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
        assertThat(getAbsolutePath().getRelative(), is("foo/bar/baz"));
        assertThat(getRelativePath().getRelative(), is("foo/bar/baz"));
        assertThat(getWithSlashes().getRelative(), is("foo/bar/baz"));
        assertThat(getAppended().getRelative(), is("foo/bar/baz"));
        assertThat(getOne().getRelative(), is("foo"));
    }

    @Test
    public void testGetParentPath() {
        assertThat(getAbsolutePath().getParentPath().getRelative(), is("foo/bar"));
        assertThat(getRelativePath().getParentPath().getRelative(), is("foo/bar"));
        assertThat(getWithSlashes().getParentPath().getRelative(), is("foo/bar"));
        assertThat(getAppended().getParentPath().getRelative(), is("foo/bar"));
        assertThat(getOne().getParentPath().getRelative(), is(""));
    }

    @Test
    public void testGetAbsolutePath() {
        assertThat(getAbsolutePath().getAbsolute(), is("/foo/bar/baz"));
        assertThat(getAbsolutePath().getParentPath().getAbsolute(), is("/foo/bar"));
    }

    @Test
    public void testEmptyPath() {
        assertThat(Path.createRoot().getName(), is(""));
        assertThat(Path.createRoot().getRelative(), is(""));
        assertThat(Path.createRoot().getParentPath().getRelative(), is(""));
        assertTrue(Path.createRoot().isRoot());
    }

    @Test
    public void testDelimiters() {
        assertThat(Path.fromString("foo/bar", ",").getName(), is("foo/bar"));
        assertThat(Path.fromString("foo/bar", "/").getName(), is("bar"));
        assertThat(Path.fromString("foo,bar", "/").getName(), is("foo,bar"));
        assertThat(Path.fromString("foo,bar", ",").getName(), is("bar"));
        assertThat(Path.createRoot(",").append("foo").append("bar").getRelative(), is("foo,bar"));
    }

    @Test
    public void testAppendPath() {
        Path p1 = getAbsolutePath();
        Path p2 = getAbsolutePath();
        Path p3 = p1.append(p2);
        assertThat(p1.getAbsolute(), is("/foo/bar/baz"));
        assertThat(p2.getAbsolute(), is("/foo/bar/baz"));
        assertThat(p3.getAbsolute(), is("/foo/bar/baz/foo/bar/baz"));
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
