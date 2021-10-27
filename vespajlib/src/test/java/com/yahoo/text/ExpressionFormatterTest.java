// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
                ")";
        assertPrettyPrint(expected, "foo(bar(baz()))", 0);
    }

    @Test
    public void testBasicDense() {
        assertPrettyPrint("foo(bar(baz()))", "foo(bar(baz()))", 50);
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
                ")";
        assertPrettyPrint(expected, "foo(bar(baz(hello world)))", 0);
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
                ")";
        assertPrettyPrint(expected, "foo(bar(baz(hello world,37)))", 0);
    }

    @Test
    public void testMultipleArgumentsSemiDense() {
        String expected =
                "foo(\n" +
                "  bar(\n" +
                "    baz(hi,37),\n" +
                "    baz(\n" +
                "      hello world,\n" +
                "      37\n" +
                "    )\n" +
                "  )\n" +
                ")";
        assertPrettyPrint(expected, "foo(bar(baz(hi,37),baz(hello world,37)))", 15);
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
                "  )";
        assertPrettyPrint(expected, "foo((bar(baz()))", 0);
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
                ")";
        assertPrettyPrint(expected, "foo(bar(baz())))", 0);
    }

    @Test
    public void testNoParenthesis() {
        String expected =
                "foo bar baz";
        assertPrettyPrint(expected, "foo bar baz", 0);
    }

    @Test
    public void testEmpty() {
        String expected =
                "";
        assertPrettyPrint(expected, "", 0);
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
                "    )";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3, 0);
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
                "    )";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3, 0);
        assertEquals(expected, pp.format("\t1:\tfoo(bar(baz(\t2:\thello world,\t3:\t37)\tt(o)@olong:\t))"));
    }

    @Test
    public void test2ColumnModeMultipleArgumentsSemiDense() {
        String expected =
                "1:  foo(\n" +
                "      bar(\n" +
                "        baz(hi,37),\n" +
                "        boz(\n" +
                "2:        hello world,\n" +
                "3:        5\n" +
                "        )\n" +
                "t(o   )\n" +
                "    )";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3, 15);
        assertEquals(expected, pp.format("\t1:\tfoo(bar(baz(hi,37),boz(\t2:\thello world,\t3:\t5)\tt(o)@olong:\t))"));
    }

    @Test
    public void test2ColumnModeMultipleArgumentsWithSpaces() {
        String expected =
                "    foo(\n" +
                "1:    bar(\n" +
                "        baz(\n" +
                "2:        hello world,\n" +
                "3:        37\n" +
                "        )\n" +
                "t(o   )\n" +
                "    )";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(3, 0);
        assertEquals(expected, pp.format("foo(\t1:\tbar(baz(\t2:\thello world, \t3:\t37)\tt(o)@olong:\t))"));
    }

    @Test
    public void testTwoColumnLambdaFunction() {
        String expected =
                "      join(\n" +
                "        a,\n" +
                "        join(\n" +
                "          b, c, f(a, b)(a * b)\n" +
                "        )\n" +
                "        , f(a, b)(a * b)\n" +
                "      )";
        ExpressionFormatter pp = ExpressionFormatter.inTwoColumnMode(5, 25);
        assertEquals(expected, pp.format("join(a, join(b, c, f(a, b)(a * b)), f(a, b)(a * b))"));
    }

    private void assertPrettyPrint(String expected, String expression, int lineLength) {
        assertEquals(expected, ExpressionFormatter.withLineLength(lineLength).format(expression));
    }

}
