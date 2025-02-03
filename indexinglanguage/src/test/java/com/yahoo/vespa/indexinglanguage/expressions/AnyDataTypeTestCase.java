package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AnyDataTypeTestCase {

    @Test
    public void testArrayAssignableTo() {
        var intArray = DataType.getArray(DataType.INT);
        var anyArray = DataType.getArray(AnyDataType.instance);
        assertTrue(intArray.isAssignableTo(intArray));
        assertTrue(anyArray.isAssignableTo(anyArray));
        assertTrue(intArray.isAssignableTo(anyArray));
        assertFalse(anyArray.isAssignableTo(intArray));

        var annArray = DataType.getArray(AnyNumericDataType.instance);
        assertTrue(annArray.isAssignableTo(annArray));
        assertFalse(annArray.isAssignableTo(intArray));
        assertTrue(annArray.isAssignableTo(anyArray));
        assertFalse(anyArray.isAssignableTo(annArray));
    }

    @Test
    public void testMapAssignableTo() {
        var intIntMap = MapDataType.getMap(DataType.INT, DataType.INT);
        var anyAnyMap = MapDataType.getMap(AnyDataType.instance, AnyDataType.instance);
        var anyIntMap = MapDataType.getMap(AnyDataType.instance, DataType.INT);
        var intAnyMap = MapDataType.getMap(DataType.INT, AnyDataType.instance);

        assertTrue(intIntMap.isAssignableTo(intIntMap));
        assertTrue(anyAnyMap.isAssignableTo(anyAnyMap));
        assertTrue(anyIntMap.isAssignableTo(anyIntMap));
        assertTrue(intAnyMap.isAssignableTo(intAnyMap));

        assertTrue(intIntMap.isAssignableTo(anyAnyMap));
        assertTrue(intIntMap.isAssignableTo(anyIntMap));
        assertTrue(intIntMap.isAssignableTo(intAnyMap));

        assertFalse(anyAnyMap.isAssignableTo(intIntMap));
        assertFalse(anyIntMap.isAssignableTo(intIntMap));
        assertFalse(intAnyMap.isAssignableTo(intIntMap));

        var annAnnMap = MapDataType.getMap(AnyNumericDataType.instance, AnyNumericDataType.instance);
        var annIntMap = MapDataType.getMap(AnyNumericDataType.instance, DataType.INT);
        var intAnnMap = MapDataType.getMap(DataType.INT, AnyNumericDataType.instance);

        assertTrue(annAnnMap.isAssignableTo(annAnnMap));
        assertTrue(annIntMap.isAssignableTo(annIntMap));
        assertTrue(intAnnMap.isAssignableTo(intAnnMap));

        assertFalse(annAnnMap.isAssignableTo(intIntMap));
        assertFalse(annIntMap.isAssignableTo(intIntMap));
        assertFalse(intAnnMap.isAssignableTo(intIntMap));

        assertTrue(annAnnMap.isAssignableTo(anyAnyMap));
        assertTrue(annIntMap.isAssignableTo(anyIntMap));
        assertTrue(intAnnMap.isAssignableTo(intAnyMap));

        assertFalse(anyAnyMap.isAssignableTo(annAnnMap));
        assertFalse(anyIntMap.isAssignableTo(annIntMap));
        assertFalse(intAnyMap.isAssignableTo(intAnnMap));
    }

}
