// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class RankingExpressionLoopDetectionTestCase {

    @Test
    @Ignore // TODO
    public void testSelfLoop() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
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
                "        macro foo() {\n" +
                "            expression: foo\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "}\n");
        try {
            builder.build();
            fail("Excepted exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In search definition 'test', rank profile 'test': The first-phase expression is invalid: Invocation loop: foo -> foo",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    @Ignore // TODO
    public void testNestedLoop() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
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
                "        macro foo() {\n" +
                "            expression: arg(5)\n" +
                "        }\n" +
                "        macro arg(a1) {\n" +
                "            expression: foo + a1*2\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "}\n");
        try {
            builder.build();
            fail("Excepted exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In search definition 'test', rank profile 'test': The first-phase expression is invalid: Invocation loop: foo -> arg(5) -> foo",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    @Ignore // TODO
    public void testSelfArgumentLoop() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
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
                "        macro foo() {\n" +
                "            expression: arg(foo)\n" +
                "        }\n" +
                "        macro arg(a1) {\n" +
                "            expression: a1*2\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "}\n");
        try {
            builder.build();
            fail("Excepted exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In search definition 'test', rank profile 'test': The first-phase expression is invalid: Invocation loop: foo -> arg(foo) -> a1 -> foo",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testNoLoopWithSameLocalArgument() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
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
                "        macro foo(a1) {\n" +
                "            expression: bar(3)\n" +
                "        }\n" +
                "        macro bar(a1) {\n" +
                "            expression: a1*2\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "}\n");
        builder.build();
    }

    @Test
    public void testNoLoopWithMultipleInvocations() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
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
                "        macro foo(a1) {\n" +
                "            expression: bar(3) + bar(a1)\n" +
                "        }\n" +
                "        macro bar(a1) {\n" +
                "            expression: a1*2\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "}\n");
        builder.build();
    }

}
