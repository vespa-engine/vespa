// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class HexEncodeTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new HexEncodeExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new HexEncodeExpression());
        assertEquals(exp.hashCode(), new HexEncodeExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new HexEncodeExpression();
        assertVerify(DataType.LONG, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected long input, but no input is specified");
        assertVerifyThrows(DataType.STRING, exp, "Expected long input, got string");
    }

    @Test
    public void requireThatInputIsEncoded() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new LongFieldValue(489210573L));
        new HexEncodeExpression().execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("1d28c2cd", ((StringFieldValue)val).getString());
    }
}
