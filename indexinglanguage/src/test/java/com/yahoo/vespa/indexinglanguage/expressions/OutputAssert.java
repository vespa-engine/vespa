// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
class OutputAssert {

    public static void assertExecute(OutputExpression exp) {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter(new Field(exp.getFieldName(), DataType.STRING)));
        ctx.setCurrentValue(new StringFieldValue("69"));
        ctx.execute(exp);

        FieldValue out = ctx.getFieldValue(exp.getFieldName());
        assertTrue(out instanceof StringFieldValue);
        assertEquals("69", ((StringFieldValue)out).getString());
    }

    public static void assertVerify(OutputExpression exp) {
        assertVerify(new MyAdapter(null), DataType.INT, exp);
        assertVerify(new MyAdapter(null), DataType.STRING, exp);
        assertVerifyThrows(new MyAdapter(null), null, exp, "Invalid expression '" + exp + "': Expected any input, but no input is specified");
        assertVerifyThrows(new MyAdapter(new VerificationException((Expression) null, "foo")), DataType.INT, exp, "Invalid expression 'null': foo");
    }

    public static void assertVerify(FieldTypeAdapter adapter, DataType value, Expression exp) {
        assertEquals(value, new VerificationContext(adapter).setCurrentType(value).verify(exp).getCurrentType());
    }

    public static void assertVerifyThrows(FieldTypeAdapter adapter, DataType value, Expression exp,
                                          String expectedException)
    {
        try {
            new VerificationContext(adapter).setCurrentType(value).verify(exp);
            fail();
        } catch (VerificationException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }

    private static class MyAdapter implements FieldTypeAdapter {

        final RuntimeException e;

        MyAdapter(RuntimeException e) {
            this.e = e;
        }

        @Override
        public DataType getInputType(Expression exp, String fieldName) {
            throw new AssertionError();
        }

        @Override
        public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
            if (e != null) {
                throw e;
            }
        }
    }
}
