// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.geo.ZCurve;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

/**
 * @author Simon Thoresen Hult
 */
public class ZCurveTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ZCurveExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new ZCurveExpression());
        assertEquals(exp.hashCode(), new ZCurveExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new ZCurveExpression();
        assertVerify(PositionDataType.INSTANCE, exp, DataType.LONG);
        assertVerifyThrows(null, exp, "Expected position input, got null.");
        assertVerifyThrows(DataType.INT, exp, "Expected position input, got int.");
    }

    @Test
    public void requireThatInputIsEncoded() {
        assertEquals(new LongFieldValue(ZCurve.encode(6, 9)),
                     new ZCurveExpression().execute(PositionDataType.valueOf(6, 9)));
    }

    @Test
    public void requireThatMissingFieldEvaluatesToNull() {
        assertNull(new ZCurveExpression().execute(PositionDataType.valueOf(null, 9)));
        assertNull(new ZCurveExpression().execute(PositionDataType.valueOf(6, null)));
    }
}
