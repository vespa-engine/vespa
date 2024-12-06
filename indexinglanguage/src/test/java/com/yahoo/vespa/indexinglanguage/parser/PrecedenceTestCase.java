// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class PrecedenceTestCase {

    @Test
    public void requireThatMathPrecedesConcat() throws ParseException {
        assertEquals("15", evaluate("1 . 2 + 3"));
        assertEquals("33", evaluate("1 + 2 . 3"));
    }

    private static String evaluate(String expressionString) throws ParseException {
        var expression = Expression.fromString(expressionString);
        // TODO: Move setInputType propagation from StatementExpression to Expression to force type resolution also when no statement?
        expression.setInputType(null, new VerificationContext(new SimpleTestAdapter()));
        return String.valueOf(expression.execute());
    }

}
