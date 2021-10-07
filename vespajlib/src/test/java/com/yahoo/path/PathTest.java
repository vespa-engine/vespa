// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.path;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
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
        assertTrue(getAbsolutePath().equals(getAbsolutePath()));
        assertTrue(getAbsolutePath().equals(getRelativePath()));
        assertTrue(getAbsolutePath().equals(getWithSlashes()));
        assertTrue(getAbsolutePath().equals(getAppended()));
        assertFalse(getAbsolutePath().equals(getOne()));

        assertTrue(getRelativePath().equals(getAbsolutePath()));
        assertTrue(getRelativePath().equals(getRelativePath()));
        assertTrue(getRelativePath().equals(getWithSlashes()));
        assertTrue(getRelativePath().equals(getAppended()));
        assertFalse(getRelativePath().equals(getOne()));

        assertTrue(getWithSlashes().equals(getAbsolutePath()));
        assertTrue(getWithSlashes().equals(getRelativePath()));
        assertTrue(getWithSlashes().equals(getWithSlashes()));
        assertTrue(getWithSlashes().equals(getAppended()));
        assertFalse(getWithSlashes().equals(getOne()));

        assertTrue(getAppended().equals(getAbsolutePath()));
        assertTrue(getAppended().equals(getRelativePath()));
        assertTrue(getAppended().equals(getWithSlashes()));
        assertTrue(getAppended().equals(getAppended()));
        assertFalse(getAppended().equals(getOne()));

        assertFalse(getOne().equals(getAbsolutePath()));
        assertFalse(getOne().equals(getRelativePath()));
        assertFalse(getOne().equals(getWithSlashes()));
        assertFalse(getOne().equals(getAppended()));
        assertTrue(getOne().equals(getOne()));
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
