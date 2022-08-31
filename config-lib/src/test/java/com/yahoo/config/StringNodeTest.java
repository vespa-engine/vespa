// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author hmusum
 */
public class StringNodeTest {

    @Test
    void testUnescapeQuotedString() {
        String a = "\"Hei\"";
        assertEquals("Hei", StringNode.unescapeQuotedString(a));
        assertEquals("foo\"bar\"", StringNode.unescapeQuotedString("foo\"bar\""));
        assertEquals("foo\"bar\"", StringNode.unescapeQuotedString("foo\\\"bar\\\""));
        assertEquals("a\rb\tc\fd", StringNode.unescapeQuotedString("a\\rb\\tc\\fd"));
        assertEquals("U", StringNode.unescapeQuotedString("\\x55"));
    }

    @Test
    void testUnescapedQuotedStringExceptions() {
        assertThrows(IllegalArgumentException.class, () -> {
            StringNode.unescapeQuotedString("foo\\");
        });
    }

    @Test
    void testToString() {
        StringNode n = new StringNode();
        assertEquals("(null)", n.toString());
        n.setValue("foo");
        assertEquals("\"foo\"", n.toString());
    }

    @Test
    void testSetValue() {
        StringNode n = new StringNode();
        n.setValue("\"foo\"");
        assertEquals("foo", n.getValue());
        n.setValue("foo");
        assertEquals("foo", n.getValue());
    }
}
