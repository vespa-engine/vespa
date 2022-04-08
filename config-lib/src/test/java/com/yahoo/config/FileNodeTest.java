// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class FileNodeTest {

    @Test
    public void testSetValue() {
        FileNode n = new FileNode();
        assertEquals("(null)", n.toString());
        assertTrue(n.doSetValue("foo.txt"));
        assertEquals("foo.txt", n.value().value());
        assertTrue(n.doSetValue("\"foo.txt\""));
        assertEquals("foo.txt", n.value().value());
        assertEquals("\"foo.txt\"", n.toString());

        assertThrows("path may not start with '..', but got: foo/../../boo",
                     IllegalArgumentException.class,
                     () -> new FileNode("foo/../../boo"));
    }

}
