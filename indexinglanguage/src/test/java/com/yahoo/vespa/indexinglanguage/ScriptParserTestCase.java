// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.EchoExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptParserTestCase {

    @Test
    public void requireThatExpressionParserCanBeInvoked() throws ParseException {
        try {
            ScriptParser.parseExpression(newContext("foo"));
        } catch (ParseException e) {
            assertException(e, "Encountered \" <IDENTIFIER> \"foo\"\" at line 1, column 1.");
        }
        assertEquals(new InputExpression("foo"),
                     ScriptParser.parseExpression(newContext("input foo")));
        assertEquals(new StatementExpression(new InputExpression("foo"), new EchoExpression()),
                     ScriptParser.parseExpression(newContext("input foo | echo")));
        assertEquals(new ScriptExpression(new StatementExpression(new InputExpression("foo")),
                                          new StatementExpression(new EchoExpression())),
                     ScriptParser.parseExpression(newContext("{ input foo; echo }")));
    }

    @Test
    public void requireThatStatementParserCanBeInvoked() throws ParseException {
        try {
            ScriptParser.parseStatement(newContext("foo"));
        } catch (ParseException e) {
            assertException(e, "Encountered \" <IDENTIFIER> \"foo\"\" at line 1, column 1.");
        }
        assertEquals(new StatementExpression(new InputExpression("foo")),
                     ScriptParser.parseStatement(newContext("input foo")));
        assertEquals(new StatementExpression(new InputExpression("foo"), new EchoExpression()),
                     ScriptParser.parseStatement(newContext("input foo | echo")));
        assertEquals(new StatementExpression(new ScriptExpression(new StatementExpression(new InputExpression("foo")),
                                                                  new StatementExpression(new EchoExpression()))),
                     ScriptParser.parseStatement(newContext("{ input foo; echo }")));
    }

    @Test
    public void requireThatScriptParserCanBeInvoked() throws ParseException {
        try {
            ScriptParser.parseScript(newContext("foo"));
        } catch (ParseException e) {
            assertException(e, "Encountered \" <IDENTIFIER> \"foo\"\" at line 1, column 1.");
        }
        try {
            ScriptParser.parseScript(newContext("input foo"));
        } catch (ParseException e) {
            assertException(e, "Encountered \" \"input\" \"input\"\" at line 1, column 1.");
        }
        try {
            ScriptParser.parseScript(newContext("input foo | echo"));
        } catch (ParseException e) {
            assertException(e, "Encountered \" \"input\" \"input\"\" at line 1, column 1.");
        }
        assertEquals(new ScriptExpression(new StatementExpression(new InputExpression("foo")),
                                          new StatementExpression(new EchoExpression())),
                     ScriptParser.parseScript(newContext("{ input foo; echo }")));
    }

    @Test
    public void requireThatStatementParserBacksUpStream() throws ParseException {
        ScriptParserContext config = newContext("input foo input bar");
        assertEquals(new StatementExpression(new InputExpression("foo")), ScriptParser.parseStatement(config));
        assertEquals(new StatementExpression(new InputExpression("bar")), ScriptParser.parseStatement(config));
    }

    @Test
    public void requireThatScriptParserBacksUpStream() throws ParseException {
        ScriptParserContext config = newContext("{ input foo }{ input bar }");
        assertEquals(new ScriptExpression(new StatementExpression(new InputExpression("foo"))),
                     ScriptParser.parseScript(config));
        assertEquals(new ScriptExpression(new StatementExpression(new InputExpression("bar"))),
                     ScriptParser.parseScript(config));
    }

    private static void assertException(ParseException e, String expectedMessage) throws ParseException {
        if (!e.getMessage().startsWith(expectedMessage)) {
            fail("Expected exception with message starting with:\n'" + expectedMessage + ", but got:\n'" + e.getMessage());
        }
    }

    private static ScriptParserContext newContext(String input) {
        return new ScriptParserContext(new SimpleLinguistics(), Embedder.throwsOnUse.asMap()).setInputStream(new IndexingInput(input));
    }

}
