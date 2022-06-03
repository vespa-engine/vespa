// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.Struct;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class PositionTypeTestCase {

    @Test
    public void requireThatPositionFactoryWorks() {
        Struct val = PositionDataType.valueOf(6, 9);
        assertEquals(new IntegerFieldValue(6), val.getFieldValue(PositionDataType.FIELD_X));
        assertEquals(new IntegerFieldValue(9), val.getFieldValue(PositionDataType.FIELD_Y));
        assertEquals(2, val.getFieldCount());

        val = PositionDataType.fromLong((6L << 32) + 9);
        assertEquals(new IntegerFieldValue(6), val.getFieldValue(PositionDataType.FIELD_X));
        assertEquals(new IntegerFieldValue(9), val.getFieldValue(PositionDataType.FIELD_Y));
        assertEquals(2, val.getFieldCount());
    }

    @Test
    public void requireThatAccessorsWork() {
        Struct val = PositionDataType.valueOf(6, 9);
        assertEquals(new IntegerFieldValue(6), PositionDataType.getXValue(val));
        assertEquals(new IntegerFieldValue(9), PositionDataType.getYValue(val));
    }

    @Test
    public void requireThatConstantsMatchCpp() {
        assertEquals("position", PositionDataType.STRUCT_NAME);
        assertEquals("x", PositionDataType.FIELD_X);
        assertEquals("y", PositionDataType.FIELD_Y);
        assertEquals("foo_zcurve", PositionDataType.getZCurveFieldName("foo"));
    }

    @Test
    public void requireFixedPointFormat() {
        assertEquals("0.0", PositionDataType.fmtD(0));
        assertEquals("123.0", PositionDataType.fmtD(123));
        assertEquals("0.000123", PositionDataType.fmtD(0.000123));
        assertEquals("0.000001", PositionDataType.fmtD(0.000001));
        assertEquals("0.0", PositionDataType.fmtD(0.0000004));
        assertEquals("999.123456", PositionDataType.fmtD(999.1234564));

        Struct val1 = PositionDataType.valueOf(0, 0);
        assertEquals("N0.0;E0.0", PositionDataType.renderAsString(val1));

        Struct val2 = PositionDataType.valueOf(-1, -1);
        assertEquals("S0.000001;W0.000001", PositionDataType.renderAsString(val2));

        Struct val3 = PositionDataType.valueOf(123456789, 87654321);
        assertEquals("N87.654321;E123.456789", PositionDataType.renderAsString(val3));

        Struct val4 = PositionDataType.valueOf(Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals("S2147.483648;W2147.483648", PositionDataType.renderAsString(val4));

        Struct val5 = PositionDataType.valueOf(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals("N2147.483647;E2147.483647", PositionDataType.renderAsString(val5));
    }
}
