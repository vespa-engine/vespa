// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class FileNodeTest {

    @Test
    void testSetValue() {
        FileNode n = new FileNode();
        assertEquals("(null)", n.toString());
        assertTrue(n.doSetValue("foo.txt"));
        assertEquals("foo.txt", n.value().value());
        assertTrue(n.doSetValue("\"foo.txt\""));
        assertEquals("foo.txt", n.value().value());
        assertEquals("\"foo.txt\"", n.toString());

        assertEquals("Path may not start with '..' but got 'foo/../../boo'",
                assertThrows(IllegalArgumentException.class, () -> new FileNode("foo/../../boo")).getMessage());
    }

}
