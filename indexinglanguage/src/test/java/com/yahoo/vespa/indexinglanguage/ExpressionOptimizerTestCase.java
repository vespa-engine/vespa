// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class ExpressionOptimizerTestCase {
    @Test
    public void requireThatSimpleExpressionsAreNotChanged() {
        assertNotOptimized("input foo");
    }

    @Test
    public void requireThatStatementsBeforeOneThatIgnoresInputAreRemoved() {
        checkStatementThatIgnoresInput(new InputExpression("foo"));
        checkStatementThatIgnoresInput(new NowExpression());
        checkStatementThatIgnoresInput(new ConstantExpression(new IntegerFieldValue(42)));
        checkStatementThatIgnoresInput(new HostNameExpression());
        checkStatementThatIgnoresInput(new RandomExpression(42));
        checkStatementThatIgnoresInput(new ArithmeticExpression(
                new NowExpression(), ArithmeticExpression.Operator.ADD, new NowExpression()));
        checkStatementThatIgnoresInput(new CatExpression(new NowExpression(), new NowExpression()));
        checkStatementThatIgnoresInput(new ParenthesisExpression(new NowExpression()));
        assertOptimized("input foo | (trim | now)", "(now)");
        assertOptimized("input foo | get_var bar", "get_var bar");
        assertOptimized("1 | index foo | now | 0", "1 | index foo | 0");
    }

    @Test
    public void requireThatStatementsBeforeOneThatConsidersInputAreKept() {
        assertNotOptimized("input foo | random");
        assertNotOptimized("input foo | trim + now");
        assertNotOptimized("input foo | (trim + now) - now");
        assertNotOptimized("input foo | (now . trim)");
        assertNotOptimized("input foo | (trim)");
        assertNotOptimized("input foo | (trim | trim)");
        assertNotOptimized("input foo | switch {case 'bar': now; case 'foo': now; }");
        assertNotOptimized("{ input test | { summary test; }; }");
        assertNotOptimized("1 | set_var foo | 0 | set_var bar");
        assertNotOptimized("1 | index foo | 0 | index bar");
        assertNotOptimized("1 | echo | 0 | echo");
        assertNotOptimized("'foo' | if (1 < 2) { now | summary } else { 42 | summary } | summary");
        assertNotOptimized("{ 0 | set_var tmp; " +
                           "  input foo | split ';' | for_each { to_int + get_var tmp | set_var tmp };" +
                           "  get_var tmp | attribute bar; }");
        assertNotOptimized("0 | set_var tmp | " +
                           "input foo | split ';' | for_each { to_int + get_var tmp | set_var tmp } | " +
                           "get_var tmp | attribute bar");
    }

    private void checkStatementThatIgnoresInput(Expression exp) {
        assertOptimized(new StatementExpression(new InputExpression("xyzzy"), exp), exp);
        assertOptimized(new StatementExpression(new InputExpression("xyzzy"), exp, new LowerCaseExpression()),
                        new StatementExpression(exp, new LowerCaseExpression()));
    }

    private void assertOptimized(Expression input, Expression expected) {
        assertEquals(expected.toString(), new ExpressionOptimizer().convert(input).toString());
    }

    private void assertOptimized(String input, String expected) {
        try {
            assertOptimized(Expression.fromString(input), Expression.fromString(expected));
        } catch (ParseException e) {
            fail(e.getMessage());
        }
    }

    private void assertNotOptimized(String script) {
        assertOptimized(script, script);
    }
}
