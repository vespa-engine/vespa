// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class ScriptTestCase {

    @Test
    public void requireThatRootProductionIsFlexible() throws ParseException {
        assertRoot(ConstantExpression.class, "1");
        assertRoot(StatementExpression.class, "1 | echo");
        assertRoot(StatementExpression.class, "{ 1 | echo }");
        assertRoot(StatementExpression.class, "{ 1 | echo; }");
        assertRoot(ScriptExpression.class, "{ 1 | echo; 2 | echo }");
    }

    @Test
    public void requireThatNewlineIsAllowedAfterStatement() throws ParseException {
        assertScript("{ 1 }");
        assertScript("{\n 1 }");
        assertScript("{ 1\n }");
        assertScript("{\n 1\n }");

        assertScript("{ 1; }");
        assertScript("{\n 1; }");
        assertScript("{ 1;\n }");
        assertScript("{\n 1;\n }");

        assertScript("{ 1; 2 }");
        assertScript("{\n 1; 2 }");
        assertScript("{\n 1;\n 2 }");
        assertScript("{\n 1; 2\n }");
        assertScript("{ 1;\n 2\n }");
        assertScript("{\n 1;\n 2\n }");
    }

    @Test
    public void requireThatNewlineIsAllowedWithinStatement() throws ParseException {
        assertStatement("1 |\n 2");
    }

    private void assertRoot(Class expectedClass, String input) throws ParseException {
        assertEquals(expectedClass, new IndexingParser(input).root().getClass());
    }

    private static void assertScript(String input) throws ParseException {
        assertNotNull(new IndexingParser(input).script());
    }

    private static void assertStatement(String input) throws ParseException {
        assertNotNull(new IndexingParser(input).statement());
    }
}
