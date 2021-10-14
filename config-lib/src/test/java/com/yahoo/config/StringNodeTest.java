// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.1.7
 */
public class StringNodeTest {

    @Test
    public void testUnescapeQuotedString() {
        String a = "\"Hei\"";
        assertThat(StringNode.unescapeQuotedString(a), is("Hei"));
        assertThat(StringNode.unescapeQuotedString("foo\"bar\""), is("foo\"bar\""));
        assertThat(StringNode.unescapeQuotedString("foo\\\"bar\\\""), is("foo\"bar\""));
        assertThat(StringNode.unescapeQuotedString("a\\rb\\tc\\fd"), is("a\rb\tc\fd"));
        assertThat(StringNode.unescapeQuotedString("\\x55"), is("U"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnescapedQuotedStringExceptions() {
        StringNode.unescapeQuotedString("foo\\");
    }

    @Test
    public void testToString() {
        StringNode n = new StringNode();
        assertThat(n.toString(), is("(null)"));
        n.setValue("foo");
        assertThat(n.toString(), is("\"foo\""));
    }

    @Test
    public void testSetValue() {
        StringNode n = new StringNode();
        n.setValue("\"foo\"");
        assertThat(n.getValue(), is("foo"));
        n.setValue("foo");
        assertThat(n.getValue(), is("foo"));
    }
}
