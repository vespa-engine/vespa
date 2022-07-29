// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RankingExpressionLoopDetectionTestCase {

    @Test
    void testSelfLoop() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo\n" +
                        "        }\n" +
                        "        function foo() {\n" +
                        "            expression: foo\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        try {
            builder.build(true);
            fail("Excepted exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'test': The function 'foo' is invalid: foo is invalid: Invocation loop: foo -> foo",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testNestedLoop() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo\n" +
                        "        }\n" +
                        "        function foo() {\n" +
                        "            expression: arg(5)\n" +
                        "        }\n" +
                        "        function arg(a1) {\n" +
                        "            expression: foo + a1*2\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        try {
            builder.build(true);
            fail("Excepted exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'test': The function 'foo' is invalid: arg(5) is invalid: foo is invalid: arg(5) is invalid: Invocation loop: arg(5) -> foo -> arg(5)",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testSelfArgumentLoop() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo\n" +
                        "        }\n" +
                        "        function foo() {\n" +
                        "            expression: arg(foo)\n" +
                        "        }\n" +
                        "        function arg(a1) {\n" +
                        "            expression: a1*2\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        try {
            builder.build(true);
            fail("Excepted exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'test': The function 'foo' is invalid: arg(foo) is invalid: a1 is invalid: foo is invalid: arg(foo) is invalid: Invocation loop: arg(foo) -> foo -> arg(foo)",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testNoLoopWithSameLocalArgument() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo(3)\n" +
                        "        }\n" +
                        "        function foo(a1) {\n" +
                        "            expression: bar(3)\n" +
                        "        }\n" +
                        "        function bar(a1) {\n" +
                        "            expression: a1*2\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
    }

    @Test
    void testNoLoopWithMultipleInvocations() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo(3)\n" +
                        "        }\n" +
                        "        function foo(a1) {\n" +
                        "            expression: bar(3) + bar(a1)\n" +
                        "        }\n" +
                        "        function bar(a1) {\n" +
                        "            expression: a1*2\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build(true);
    }

    @Test
    void testNoLoopWithBoundIdentifiers() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "    }\n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo(bar(2))\n" +
                        "        }\n" +
                        "        function foo(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        function bar(x) {\n" +
                        "            expression: x + x\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);
    }

    @Test
    void testNoLoopWithTheSameNestedIdentifierWhichIsUnbound() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "    }\n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo()\n" +
                        "        }\n" +
                        "        function foo() {\n" +
                        "            expression: bar(x)\n" +
                        "        }\n" +
                        "        function bar(x) {\n" +
                        "            expression: x + x\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);
    }

    @Test
    void testNoLoopWithTheSameAlternatingNestedIdentifierWhichIsUnbound() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "    }\n" +
                        "    rank-profile test {\n" +
                        "        first-phase {\n" +
                        "            expression: foo()\n" +
                        "        }\n" +
                        "        function foo() {\n" +
                        "            expression: bar(x)\n" +
                        "        }\n" +
                        "        function bar(y) {\n" +
                        "            expression: baz(y)\n" +
                        "        }\n" +
                        "        function baz(x) {\n" +
                        "            expression: x + x\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);
    }

}
