// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author hmusum
 * @since 5.1.7
 */
public class StringNodeTest {

    @Test
    public void testUnescapeQuotedString() {
        String a = "\"Hei\"";
        assertEquals("Hei", StringNode.unescapeQuotedString(a));
        assertEquals("foo\"bar\"", StringNode.unescapeQuotedString("foo\"bar\""));
        assertEquals("foo\"bar\"", StringNode.unescapeQuotedString("foo\\\"bar\\\""));
        assertEquals("a\rb\tc\fd", StringNode.unescapeQuotedString("a\\rb\\tc\\fd"));
        assertEquals("U", StringNode.unescapeQuotedString("\\x55"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnescapedQuotedStringExceptions() {
        StringNode.unescapeQuotedString("foo\\");
    }

    @Test
    public void testToString() {
        StringNode n = new StringNode();
        assertEquals("(null)", n.toString());
        n.setValue("foo");
        assertEquals("\"foo\"", n.toString());
    }

    @Test
    public void testSetValue() {
        StringNode n = new StringNode();
        n.setValue("\"foo\"");
        assertEquals("foo", n.getValue());
        n.setValue("foo");
        assertEquals("foo", n.getValue());
    }
}
