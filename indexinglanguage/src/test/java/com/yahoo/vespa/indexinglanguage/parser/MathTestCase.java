// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class MathTestCase {

    private static final double EPS = 1e-6;

    @Test
    public void requireThatNumericTypeIsPreserved() throws ParseException {
        assertEquals(1, evaluateDouble("1.0"), EPS);
        assertEquals(3, evaluateDouble("1.0 + 2"), EPS);
        assertEquals(-1, evaluateDouble("1.0 - 2"), EPS);
        assertEquals(2, evaluateDouble("1.0 * 2"), EPS);
        assertEquals(0.5, evaluateDouble("1.0 / 2"), EPS);
        assertEquals(1, evaluateDouble("1.0 % 2"), EPS);

        assertEquals(1, evaluateFloat("1.0f"), EPS);
        assertEquals(3, evaluateFloat("1.0f + 2"), EPS);
        assertEquals(-1, evaluateFloat("1.0f - 2"), EPS);
        assertEquals(2, evaluateFloat("1.0f * 2"), EPS);
        assertEquals(0.5, evaluateFloat("1.0f / 2"), EPS);
        assertEquals(1, evaluateFloat("1.0f % 2"), EPS);

        assertEquals(1, evaluateInteger("1"));
        assertEquals(3, evaluateInteger("1 + 2"));
        assertEquals(-1, evaluateInteger("1 - 2"));
        assertEquals(2, evaluateInteger("1 * 2"));
        assertEquals(0, evaluateInteger("1 / 2"));
        assertEquals(1, evaluateInteger("1 % 2"));

        assertEquals(1, evaluateLong("1L"));
        assertEquals(3, evaluateLong("1L + 2"));
        assertEquals(-1, evaluateLong("1L - 2"));
        assertEquals(2, evaluateLong("1L * 2"));
        assertEquals(0, evaluateLong("1L / 2"));
        assertEquals(1, evaluateLong("1L % 2"));

        assertEquals(3, evaluateDouble("1.0 + 2"), EPS);
        assertEquals(3, evaluateDouble("1.0 + 2L"), EPS);
        assertEquals(3, evaluateDouble("1.0 + 2.0f"), EPS);
        assertEquals(3, evaluateFloat("1.0f + 2"), EPS);
        assertEquals(3, evaluateFloat("1.0f + 2L"), EPS);
        assertEquals(3, evaluateLong("1L + 2"));
    }

    @Test
    public void requireThatParenthesisControlsPrecedence() throws ParseException {
        assertEquals(2, evaluateInteger("1 - 2 + 3"));
        assertEquals(2, evaluateInteger("(1 - 2) + 3"));
        assertEquals(-4, evaluateInteger("1 - (2 + 3)"));
        assertEquals(2, evaluateInteger("(1 - 2 + 3)"));
    }

    private static double evaluateDouble(String script) throws ParseException {
        FieldValue val = evaluateMath(script);
        assertTrue(val instanceof DoubleFieldValue);
        return ((DoubleFieldValue)val).getDouble();
    }

    private static double evaluateFloat(String script) throws ParseException {
        FieldValue val = evaluateMath(script);
        assertTrue(val instanceof FloatFieldValue);
        return ((FloatFieldValue)val).getFloat();
    }

    private static long evaluateInteger(String script) throws ParseException {
        FieldValue val = evaluateMath(script);
        assertTrue(val instanceof IntegerFieldValue);
        return ((IntegerFieldValue)val).getInteger();
    }

    private static long evaluateLong(String script) throws ParseException {
        FieldValue val = evaluateMath(script);
        assertTrue(val instanceof LongFieldValue);
        return ((LongFieldValue)val).getLong();
    }

    private static FieldValue evaluateMath(String script) throws ParseException {
        return Expression.fromString(script).execute();
    }
}
