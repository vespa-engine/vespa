// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class FileNodeTest {
    @Test
    public void testSetValue() {
        FileNode n = new FileNode();
        assertThat(n.toString(), is("(null)"));
        assertTrue(n.doSetValue("foo.txt"));
        assertThat(n.value().value(), is("foo.txt"));
        assertTrue(n.doSetValue("\"foo.txt\""));
        assertThat(n.value().value(), is("foo.txt"));
        assertThat(n.toString(), is("\"foo.txt\""));
    }
}
