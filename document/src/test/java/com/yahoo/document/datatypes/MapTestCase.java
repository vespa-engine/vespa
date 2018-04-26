// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import java.util.Map;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;


public class MapTestCase {

    @Test
    public void testStringMap() {
        MapDataType mapType = new MapDataType(DataType.STRING, DataType.STRING);
        MapFieldValue<StringFieldValue, StringFieldValue> map = mapType.createFieldValue();
        StringFieldValue sfvk1=new StringFieldValue("k1");
        StringFieldValue sfvk2=new StringFieldValue("k2");
        StringFieldValue sfvk3=new StringFieldValue("k3");
        StringFieldValue sfvv1=new StringFieldValue("v1");
        StringFieldValue sfvv2=new StringFieldValue("v2");
        StringFieldValue sfvv3=new StringFieldValue("v3");
        map.put(sfvk1, sfvv1);
        map.put(sfvk2, sfvv2);
        map.put(sfvk3, sfvv3);
        assertEquals(map.get(sfvk1), sfvv1);
        assertEquals(map.get(sfvk2), sfvv2);
        assertEquals(map.get(sfvk3), sfvv3);
        assertEquals(map.get(new StringFieldValue("k1")).getWrappedValue(), "v1");
        assertEquals(map.get(new StringFieldValue("k2")).getWrappedValue(), "v2");
        assertEquals(map.get(new StringFieldValue("k3")).getWrappedValue(), "v3");
    }

    @Test
    public void testAdvancedMap() {
        MapDataType stringMapType1 = new MapDataType(DataType.STRING, DataType.STRING);
        MapDataType stringMapType2 = new MapDataType(DataType.STRING, DataType.STRING);
        MapFieldValue sm1 = stringMapType1.createFieldValue();
        MapFieldValue sm2 = stringMapType2.createFieldValue();
        StringFieldValue e = new StringFieldValue("e");
        StringFieldValue g = new StringFieldValue("g");
        sm1.put(new StringFieldValue("a"), new StringFieldValue("b"));
        sm1.put(new StringFieldValue("c"), new StringFieldValue("d"));
        sm2.put(e, new StringFieldValue("f"));
        sm2.put(g, new StringFieldValue("h"));

        StructDataType structType= new StructDataType("teststr");
        structType.addField(new Field("int", DataType.INT));
        structType.addField(new Field("flt", DataType.FLOAT));
        Struct s = structType.createFieldValue();
        s.setFieldValue("int", 99);
        s.setFieldValue("flt", -89.345);

        ArrayDataType twoDimArray = DataType.getArray(DataType.getArray(DataType.FLOAT));
        Array tda = twoDimArray.createFieldValue();

        MapDataType floatToTwoDimArray = new MapDataType(DataType.FLOAT, twoDimArray);
        MapDataType stringToStruct = new MapDataType(DataType.STRING, structType);
        MapDataType stringMapToStringMap = new MapDataType(stringMapType1, stringMapType2);

        MapFieldValue f2tda = floatToTwoDimArray.createFieldValue();
        f2tda.put(new FloatFieldValue(3.4f), tda);

        MapFieldValue s2sct = stringToStruct.createFieldValue();
        s2sct.put(new StringFieldValue("s1"), s);
        MapFieldValue sm2sm = stringMapToStringMap.createFieldValue();
        sm2sm.put(sm1, sm2);

        assertEquals(f2tda.get(new FloatFieldValue(3.4f)), tda);
        assertEquals(new IntegerFieldValue(99), ((Struct) (s2sct.get(new StringFieldValue("s1")))).getFieldValue("int"));
        assertEquals(new StringFieldValue("f"), ((MapFieldValue)(sm2sm.get(sm1))).get(e));
        assertEquals(new StringFieldValue("h"), ((MapFieldValue)(sm2sm.get(sm1))).get(g));

        // Look up using different map w same contents
        // TODO it works even if sm1_2 is empty, something with class id?
        MapFieldValue sm1_2 = stringMapType1.createFieldValue();
        sm1_2.put(new StringFieldValue("a"), new StringFieldValue("b"));
        sm1_2.put(new StringFieldValue("c"), new StringFieldValue("d"));
        assertEquals(new StringFieldValue ("f"), ((MapFieldValue)(sm2sm.get(sm1_2))).get(e));
        assertEquals(new StringFieldValue("h"), ((MapFieldValue)(sm2sm.get(sm1_2))).get(g));

    }

    @Test
    public void testSerializationStringMap() {
        MapDataType mapType = new MapDataType(DataType.STRING, DataType.STRING);
        MapFieldValue<StringFieldValue, StringFieldValue> map = mapType.createFieldValue();
        //Field f = new Field("stringmap",mapType);
        StringFieldValue sfvk1=new StringFieldValue("k1");
        StringFieldValue sfvk2=new StringFieldValue("k2");
        StringFieldValue sfvk3=new StringFieldValue("k3");
        StringFieldValue sfvv1=new StringFieldValue("v1");
        StringFieldValue sfvv2=new StringFieldValue("v2");
        StringFieldValue sfvv3=new StringFieldValue("v3");
        map.put(sfvk1, sfvv1);
        map.put(sfvk2, sfvv2);
        map.put(sfvk3, sfvv3);
        assertCorrectSerialization(mapType, map);
    }

    @Test
    public void testSerializationComplex() {
        ArrayDataType twoDimArray = DataType.getArray(DataType.getArray(DataType.FLOAT));
        MapDataType floatToTwoDimArray = new MapDataType(DataType.FLOAT, twoDimArray);
        MapFieldValue<FloatFieldValue, Array<Array<FloatFieldValue>>> map = floatToTwoDimArray.createFieldValue();

        Array<FloatFieldValue> af1 = new Array(DataType.getArray(DataType.FLOAT));
        af1.add(new FloatFieldValue(11f));
        af1.add(new FloatFieldValue(111f));
        Array<FloatFieldValue> af2 = new Array(DataType.getArray(DataType.FLOAT));
        af2.add(new FloatFieldValue(22f));
        af2.add(new FloatFieldValue(222f));
        Array<Array<FloatFieldValue>> aaf1 = new Array(twoDimArray);
        aaf1.add(af1);
        aaf1.add(af2);

        Array<FloatFieldValue> af3 = new Array(DataType.getArray(DataType.FLOAT));
        af3.add(new FloatFieldValue(33f));
        af3.add(new FloatFieldValue(333f));
        Array<FloatFieldValue> af4 = new Array(DataType.getArray(DataType.FLOAT));
        af4.add(new FloatFieldValue(44f));
        af4.add(new FloatFieldValue(444f));
        Array<Array<FloatFieldValue>> aaf2 = new Array(twoDimArray);
        aaf2.add(af3);
        aaf2.add(af4);

        map.put(new FloatFieldValue(1.1f), aaf1);
        map.put(new FloatFieldValue(2.2f), aaf2);
        assertCorrectSerialization(floatToTwoDimArray, map);
    }

    private void assertCorrectSerialization(MapDataType mapType, MapFieldValue<? extends FieldValue, ? extends FieldValue> map) {
        Field f = new Field("", mapType);
        DocumentTypeManager man = new DocumentTypeManager();
        man.register(mapType);
        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create42(buffer);
        serializer.write(f, map);
        buffer.flip();
        DocumentDeserializer deserializer = DocumentDeserializerFactory.create42(man, buffer);
        MapFieldValue<FieldValue, FieldValue> map2 = new MapFieldValue<FieldValue, FieldValue>(mapType);
        deserializer.read(f, map2);
        assertNotSame(map, map2);
        for (Map.Entry<?,?> e : map.entrySet()) {
            assertEquals(e.getValue(), map2.get(e.getKey()));
        }
    }

    @Test
    public void testIllegalMapAssignment() {
        MapDataType type1 = new MapDataType(DataType.INT, DataType.INT);
        MapDataType type2 = new MapDataType(DataType.STRING, DataType.STRING);
        MapFieldValue map1 = new MapFieldValue(type1);
        map1.put(new IntegerFieldValue(42), new IntegerFieldValue(84));
        MapFieldValue map2 = new MapFieldValue(type2);
        try {
            map2.assign(map1);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Incompatible data types. Got datatype int (code: 0),"
                         + " expected datatype string (code: 2)",
                         e.getMessage());
        }
    }

}
