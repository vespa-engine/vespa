// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ExpressionFormatterTest {

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
    public void testArgument() {
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
    public void testMultipleArguments() {
        String expected =
                "foo(\n" +
                "  bar(\n" +
                "    baz(\n" +
                "      hello world,\n" +
                "      37\n" +
                "    )\n" +
                "  )\n" +
                ")\n";
        assertPrettyPrint(expected, "foo(bar(baz(hello world,37)))");
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
                "  )\n";
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

    @Test
    public void test2ColumnMode() {
        String expected =
                "1:  foo(\n" +
                "      bar(\n" +
                "        baz(\n" +
                "2:        hello world\n" +
                "        )\n" +
                "t(o   )\n" +
                "    )\n";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3);
        assertEquals(expected, pp.format("\t1:\tfoo(bar(baz(\t2:\thello world)\tt(o)@olong:\t))"));
    }

    @Test
    public void test2ColumnModeMultipleArguments() {
        String expected =
                "1:  foo(\n" +
                "      bar(\n" +
                "        baz(\n" +
                "2:        hello world,\n" +
                "3:        37\n" +
                "        )\n" +
                "t(o   )\n" +
                "    )\n";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3);
        assertEquals(expected, pp.format("\t1:\tfoo(bar(baz(\t2:\thello world,\t3:\t37)\tt(o)@olong:\t))"));
    }

    @Test
    public void test2ColumnModeMultipleArgumentsWithSpaces() {
        String expected =
                "1:  foo(\n" +
                "      bar(\n" +
                "        baz(\n" +
                "2:        hello world,\n" +
                "3:        37\n" +
                "        )\n" +
                "t(o   )\n" +
                "    )\n";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3);
        assertEquals(expected, pp.format("\t1:\tfoo(bar(baz(\t2:\thello world, \t3:\t37)\tt(o)@olong:\t))"));
    }

    private void assertPrettyPrint(String expected, String expression) {
        assertEquals(expected, ExpressionFormatter.on(expression));
    }

}
