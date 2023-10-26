// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SubstringTestCase {

    @Test
    public void requireThatAccessorsWork() {
        SubstringExpression exp = new SubstringExpression(6, 9);
        assertEquals(6, exp.getFrom());
        assertEquals(9, exp.getTo());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new SubstringExpression(6, 9);
        assertNotEquals(exp, new Object());
        assertNotEquals(exp, new SubstringExpression(66, 99));
        assertNotEquals(exp, new SubstringExpression(6, 99));
        assertEquals(exp, new SubstringExpression(6, 9));
        assertEquals(exp.hashCode(), new SubstringExpression(6, 9).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new SubstringExpression(6, 9);
        assertVerify(DataType.STRING, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected string input, but no input is specified");
        assertVerifyThrows(DataType.INT, exp, "Expected string input, got int");
    }

    @Test
    public void requireThatRangeIsCappedToInput() {
        assertEquals(new StringFieldValue(""), new SubstringExpression(6, 9).execute(new StringFieldValue("012345")));
        assertEquals(new StringFieldValue("345"), new SubstringExpression(3, 9).execute(new StringFieldValue("012345")));
    }

    @Test
    public void requireThatIllegalRangeThrowsException() {
        assertIndexOutOfBounds(-1, 69);
        assertIndexOutOfBounds(69, -1);
        assertIndexOutOfBounds(-9, -6);
        assertIndexOutOfBounds(9, 6);
    }

    private static void assertIndexOutOfBounds(int from, int to) {
        try {
            new SubstringExpression(from, to);
            fail();
        } catch (IndexOutOfBoundsException e) {

        }
    }

    @Test
    public void requireThatStringIsSliced() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setValue(new StringFieldValue("666999"));
        new SubstringExpression(2, 4).execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("69", ((StringFieldValue)val).getString());
    }
}
