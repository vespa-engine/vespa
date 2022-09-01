// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author gjoranv
 */
public class PathNodeTest {

    @Test
    void testSetValue() {
        PathNode n = new PathNode();
        assertEquals("(null)", n.toString());

        n = new PathNode(new FileReference("foo.txt"));
        assertEquals(new File("foo.txt").toPath(), n.value());

        assertEquals("Path may not start with '..' but got 'foo/../../boo'",
                assertThrows(IllegalArgumentException.class, () -> new PathNode(new FileReference("foo/../../boo"))).getMessage());
    }

}
