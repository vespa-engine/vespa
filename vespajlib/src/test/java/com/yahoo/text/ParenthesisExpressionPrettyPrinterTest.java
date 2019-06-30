// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ParenthesisExpressionPrettyPrinterTest {

    @Test
    public void testBasic() {
        String expected =
                "foo(\n" +
                "  bar(\n" +
                "    baz(\n" +
                "    )\n" +
                "  )\n" +
                ")\n";
        assertPrettyPrint(expected, "foo(bar(baz()))");
    }

    @Test
    public void testInnerContent() {
        String expected =
                "foo(\n" +
                "  bar(\n" +
                "    baz(\n" +
                "      hello world\n" +
                "    )\n" +
                "  )\n" +
                ")\n";
        assertPrettyPrint(expected, "foo(bar(baz(hello world)))");
    }
    @Test
    public void testUnmatchedStart() {
        String expected =
                "foo(\n" +
                "  (\n" +
                "    bar(\n" +
                "      baz(\n" +
                "      )\n" +
                "    )\n" +
                "  )\n" +
                "  ";
        assertPrettyPrint(expected, "foo((bar(baz()))");
    }

    @Test
    public void testUnmatchedEnd() {
        String expected =
                "foo(\n" +
                "  bar(\n" +
                "    baz(\n" +
                "    )\n" +
                "  )\n" +
                ")\n" +
                ")\n";
        assertPrettyPrint(expected, "foo(bar(baz())))");
    }

    @Test
    public void testNoParenthesis() {
        String expected =
                "foo bar baz";
        assertPrettyPrint(expected, "foo bar baz");
    }

    @Test
    public void testEmpty() {
        String expected =
                "";
        assertPrettyPrint(expected, "");
    }

    private void assertPrettyPrint(String expected, String expression) {
        assertEquals(expected, ParenthesisExpressionPrettyPrinter.prettyPrint(expression));
    }

}
