// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.Struct;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
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
        assertEquals("foo.position", PositionDataType.getPositionSummaryFieldName("foo"));
        assertEquals("foo.distance", PositionDataType.getDistanceSummaryFieldName("foo"));
    }
}
