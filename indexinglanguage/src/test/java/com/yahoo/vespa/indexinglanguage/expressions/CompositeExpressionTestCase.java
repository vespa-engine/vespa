// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class CompositeExpressionTestCase {

    @Test
    public void requireThatToScriptBlockOutputIsParsable() throws ParseException {
        Expression exp = new ConstantExpression(new IntegerFieldValue(69));
        assertScript("{ 69; }", exp);
        assertScript("{ 69; }", new StatementExpression(exp));
        assertScript("{ 69; }", new ScriptExpression(new StatementExpression(exp)));
    }

    private static void assertScript(String expectedScript, Expression exp) throws ParseException {
        String str = CompositeExpression.toScriptBlock(exp);
        assertEquals(expectedScript, str);
        assertNotNull(ScriptExpression.fromString(str));
    }
}
