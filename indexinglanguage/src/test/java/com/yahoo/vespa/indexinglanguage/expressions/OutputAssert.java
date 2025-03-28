// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
class OutputAssert {

    public static void assertVerify(OutputExpression exp) {
        assertVerify(new MyAdapter(null), DataType.INT, exp);
        assertVerify(new MyAdapter(null), DataType.STRING, exp);
        assertVerifyThrows(new MyAdapter(null), null, exp, "Invalid expression '" + exp + "': Expected input, but no input is specified");
        assertVerifyThrows(new MyAdapter(new VerificationException((Expression) null, "foo")), DataType.INT, exp, "Invalid expression 'null': foo");
    }

    public static void assertVerify(FieldTypes adapter, DataType value, Expression exp) {
        var context = new TypeContext(adapter);
        assertEquals(value, exp.setInputType(value, context));
    }

    public static void assertVerifyThrows(FieldTypes adapter, DataType value, Expression exp,
                                          String expectedException)
    {
        try {
            new TypeContext(adapter).resolve(exp);
            fail();
        } catch (VerificationException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }

    private static class MyAdapter implements FieldTypes {

        final RuntimeException e;

        MyAdapter(RuntimeException e) {
            this.e = e;
        }

        @Override
        public DataType getFieldType(String fieldName, Expression exp) {
            throw new AssertionError();
        }

    }

}
