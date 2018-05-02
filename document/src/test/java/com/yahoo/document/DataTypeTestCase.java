// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yahoo.document.datatypes.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class DataTypeTestCase {

    @Test
    public void testWeightedSetTypes() {
        DataType stringDefault = DataType.getWeightedSet(DataType.STRING);
        DataType stringTag=DataType.getWeightedSet(DataType.STRING,true,true);
        assertTrue(stringDefault.equals(stringDefault));
        assertTrue(stringTag.equals(stringTag));
        assertFalse(stringDefault.equals(stringTag));
        assertEquals("WeightedSet<string>",stringDefault.getName());
        assertEquals(18, stringTag.getCode());
        //assertEquals("WeightedSet<string>;Add;Remove",stringTag.getName());
        assertEquals("tag",stringTag.getName());
    }

    @Test
    public void testTagType() {
        DocumentTypeManager manager = new DocumentTypeManager();
        //assertEquals(DataType.getWeightedSet(DataType.STRING,true,true),DataType.TAG.getBaseType());
        assertNotNull(manager.getDataType("tag"));
        assertEquals(DataType.TAG, manager.getDataType("tag"));
        assertEquals(manager.getDataType("tag").getCode(), 18);
        assertEquals(DataType.getWeightedSet(DataType.STRING,true,true).getCode(), 18);
    }

    @Test
    public void requireThatPredicateDataTypeIsNamedPredicate() {
        assertEquals("predicate", DataType.PREDICATE.getName());
    }

    @Test
    public void requireThatPredicateDataTypeHasId20() {
        assertEquals(20, DataType.PREDICATE.getId());
    }

    @Test
    public void requireThatPredicateDataTypeYieldsPredicateFieldValue() {
        assertEquals(PredicateFieldValue.class, DataType.PREDICATE.createFieldValue().getClass());
    }

    @Test
    public void testCreateFieldValueWithArg() {
        {
            ByteFieldValue bfv = (ByteFieldValue) DataType.BYTE.createFieldValue((byte) 4);
            assertEquals((byte) 4, bfv.getByte());
        }
        {
            ByteFieldValue bfv2 = (ByteFieldValue) DataType.BYTE.createFieldValue(4);
            assertEquals((byte) 4, bfv2.getByte());
        }
        {
            DoubleFieldValue dfv = (DoubleFieldValue) DataType.DOUBLE.createFieldValue(5.5d);
            assertEquals(5.5d, dfv.getDouble(), 1E-6);
        }
        {
            FloatFieldValue ffv = (FloatFieldValue) DataType.FLOAT.createFieldValue(5.5f);
            assertEquals(5.5f, ffv.getFloat(), 1E-6);
        }
        {
            IntegerFieldValue ifv = (IntegerFieldValue) DataType.INT.createFieldValue(5);
            assertEquals(5, ifv.getInteger());
        }
        {
            LongFieldValue lfv = (LongFieldValue) DataType.LONG.createFieldValue(34L);
            assertEquals(34L, lfv.getLong());
        }
        {
            StringFieldValue sfv = (StringFieldValue) DataType.STRING.createFieldValue("foo");
            assertEquals("foo", sfv.getString());
        }
        // TODO: the 3 following should be made to not throw a silent ReflectiveOperationException in createFieldValue
        {
            Map<String, Integer> wsetMap = new LinkedHashMap<>();
            wsetMap.put("foo", 1);
            WeightedSet<StringFieldValue> ws = (WeightedSet<StringFieldValue>) DataType.getWeightedSet(DataType.STRING).createFieldValue(wsetMap);
            assertEquals(ws.get(new StringFieldValue("foo")), Integer.valueOf(1));
        }
        {
            List<String> arrayArray = new ArrayList<>();
            arrayArray.add("foo");
            Array<StringFieldValue> array = (Array<StringFieldValue>) DataType.getArray(DataType.STRING).createFieldValue(arrayArray);
            assertEquals(array.get(0), new StringFieldValue("foo"));
        }
        {
            Map<String, String> mapMap = new LinkedHashMap<>();
            mapMap.put("foo", "bar");
            MapFieldValue<StringFieldValue, StringFieldValue> map = (MapFieldValue<StringFieldValue, StringFieldValue>) DataType.getMap(DataType.STRING, DataType.STRING).createFieldValue(mapMap);
            assertEquals(map.get(new StringFieldValue("foo")), new StringFieldValue("bar"));
        }
    }

    @Test
    public void testIllegalFieldPathsInArrayDataType() {
        ArrayDataType adt = DataType.getArray(DataType.STRING);
        try {
            adt.buildFieldPath("[      ");
            fail("Should have gotten exception for illegal field path.");
        } catch (IllegalArgumentException iae) {
            // ok!
        }
    }

    @Test
    public void testCloningArrayDataType() {
        ArrayDataType adt = DataType.getArray(DataType.STRING);
        ArrayDataType adtClone = adt.clone();

        assertNotSame(adt, adtClone);
        assertEquals(adt, adtClone);
        assertEquals(adt.getNestedType(), adtClone.getNestedType());
        //we should consider NOT cloning primitive types, but they are mutable and just ugly, so just clone them
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInstantiatingArray() {
        ArrayDataType adt = DataType.getArray(DataType.STRING);
        Array<StringFieldValue> val = adt.createFieldValue();
        val.add(new StringFieldValue("foobar"));
        assertEquals(1, val.size());
    }

    @Test
    public void requireThatCompareToIsImplemented() {
        assertEquals(0, DataType.INT.compareTo(DataType.INT));
        assertTrue(DataType.INT.compareTo(DataType.STRING) < 0);
        assertTrue(DataType.STRING.compareTo(DataType.INT) > 0);
    }
}
