// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class StructTestCase {
    @Test
    public void testBasicStuff() throws Exception {
        StructDataType type = new StructDataType("teststr");
        type.addField(new Field("int", 0, DataType.INT));
        type.addField(new Field("flt", 1, DataType.FLOAT));
        type.addField(new Field("str", 2, DataType.STRING));
        type.addField(new Field("raw", 3, DataType.RAW));
        type.addField(new Field("lng", 4, DataType.LONG));
        type.addField(new Field("dbl", 5, DataType.DOUBLE));
        type.addField(new Field("uri", 6, DataType.URI));
        type.addField(new Field("byt", 8, DataType.BYTE));

        Struct struct = new Struct(type);
        {
            //add and remove again:
            assertEquals(0, struct.getFields().size());
            IntegerFieldValue ifv = new IntegerFieldValue(5);
            struct.setFieldValue("int", ifv);
            assertEquals(1, struct.getFields().size());
            struct.removeFieldValue("int");
            assertEquals(0, struct.getFields().size());
        }

        {
            //add three elements and remove one of them, and replace the last one:
            assertEquals(0, struct.getFields().size());

            IntegerFieldValue ifv = new IntegerFieldValue(5);
            struct.setFieldValue("int", ifv);
            assertEquals(1, struct.getFields().size());

            FloatFieldValue ffv = new FloatFieldValue(5.0f);
            struct.setFieldValue("flt", ffv);
            assertEquals(2, struct.getFields().size());

            DoubleFieldValue dfv = new DoubleFieldValue(6.0d);
            struct.setFieldValue("dbl", dfv);
            assertEquals(3, struct.getFields().size());

            Iterator<Map.Entry<Field, FieldValue>> it = struct.iterator();
            assertSame(ifv, it.next().getValue());
            assertSame(ffv, it.next().getValue());
            assertSame(dfv, it.next().getValue());
            assertFalse(it.hasNext());

            struct.removeFieldValue("flt");
            assertEquals(2, struct.getFields().size());

            it = struct.iterator();
            assertSame(ifv, it.next().getValue());
            assertSame(dfv, it.next().getValue());
            assertFalse(it.hasNext());

            DoubleFieldValue dfv2 = new DoubleFieldValue(9.0d);
            struct.setFieldValue("dbl", dfv2);
            assertEquals(2, struct.getFields().size());

            it = struct.iterator();
            assertSame(ifv, it.next().getValue());
            assertSame(dfv2, it.next().getValue());
            assertFalse(it.hasNext());
        }
    }

    @Test
    public void testSetGetPrimitiveTypes() throws Exception {
        StructDataType type = new StructDataType("teststr");
        type.addField(new Field("int", DataType.INT));
        type.addField(new Field("flt", DataType.FLOAT));
        type.addField(new Field("str", DataType.STRING));
        type.addField(new Field("raw", DataType.RAW));
        type.addField(new Field("lng", DataType.LONG));
        type.addField(new Field("dbl", DataType.DOUBLE));
        type.addField(new Field("uri", DataType.URI));
        type.addField(new Field("byt", DataType.BYTE));

        Struct struct = new Struct(type);

        {
            IntegerFieldValue nt = new IntegerFieldValue(544);
            Object o = struct.setFieldValue("int", nt);
            assertNull(o);
            assertEquals(new IntegerFieldValue(544), struct.getFieldValue("int"));
            o = struct.setFieldValue("int", 500);
            assertEquals(nt, o);
            assertFalse(nt.equals(struct.getFieldValue("int")));
        }
        {
            FloatFieldValue flt = new FloatFieldValue(5.44f);
            Object o = struct.setFieldValue("flt", flt);
            assertNull(o);
            assertEquals(flt, struct.getFieldValue("flt"));
            o = struct.setFieldValue("flt", new FloatFieldValue(5.00f));
            assertEquals(flt, o);
            assertFalse(flt.equals(struct.getFieldValue("flt")));
        }
        {
            StringFieldValue string = new StringFieldValue("this is a string");
            Object o = struct.setFieldValue("str", string);
            assertNull(o);
            assertEquals(string, struct.getFieldValue("str"));
            o = struct.setFieldValue("str", "another string");
            assertEquals(string, o);
            assertSame(string, o);
            assertFalse(string.equals(struct.getFieldValue("str")));
        }
        {
            Raw buf = new Raw(ByteBuffer.wrap(new byte[100]));
            Object o = struct.setFieldValue("raw", buf);
            assertNull(o);
            assertEquals(buf, struct.getFieldValue("raw"));
            o = struct.setFieldValue("raw", new Raw(ByteBuffer.wrap(new byte[50])));
            assertEquals(buf, o);
            assertSame(buf, o);
            assertFalse(buf.equals(struct.getFieldValue("raw")));
        }
        {
            LongFieldValue lng = new LongFieldValue(59879879879079L);
            Object o = struct.setFieldValue("lng", lng);
            assertEquals(lng, struct.getFieldValue("lng"));
            o = struct.setFieldValue("lng", new LongFieldValue(23418798734243L));
            assertEquals(lng, o);
            assertFalse(lng.equals(struct.getFieldValue("lng")));
        }
        {
            DoubleFieldValue dbl = new DoubleFieldValue(5.44d);
            Object o = struct.setFieldValue("dbl", dbl);
            assertNull(o);
            assertEquals(dbl, struct.getFieldValue("dbl"));
            o = struct.setFieldValue("dbl", new DoubleFieldValue(5.00d));
            assertEquals(dbl, o);
            assertFalse(dbl.equals(struct.getFieldValue("dbl")));
        }
        {
            UriFieldValue uri = new UriFieldValue("this is a uri");
            Object o = struct.setFieldValue("uri", uri);
            assertNull(o);
            assertEquals(uri, struct.getFieldValue("uri"));
            o = struct.setFieldValue("uri", "another uri");
            assertEquals(uri, o);
            assertSame(uri, o);
            assertFalse(uri.equals(struct.getFieldValue("uri")));
        }
        {
            ByteFieldValue byt = new ByteFieldValue((byte)123);
            Object o = struct.setFieldValue("byt", byt);
            assertNull(o);
            assertEquals(byt, struct.getFieldValue("byt"));
            o = struct.setFieldValue("byt", (byte) 100);
            assertEquals(byt, o);
            assertFalse(byt.equals(struct.getFieldValue("byt")));
        }
    }

    @Test
    public void testSetGetAggregateTypes() throws Exception {
        StructDataType type = new StructDataType("teststr");
        type.addField(new Field("intarray", DataType.getArray(DataType.INT)));
        type.addField(new Field("strws", DataType.getWeightedSet(DataType.STRING)));

        Struct struct = new Struct(type);

        {
            //TEST USING OUR IMPLEMENTATION OF LIST
            Array integerArray = new Array(type.getField("intarray").getDataType());
            integerArray.add(new IntegerFieldValue(5));
            integerArray.add(new IntegerFieldValue(10));
            assertEquals(2, integerArray.size());
            struct.setFieldValue("intarray", integerArray);
            assertEquals(2, integerArray.size());
            List outList = (List) struct.getFieldValue("intarray");
            integerArray.add(new IntegerFieldValue(322));
            integerArray.add(new IntegerFieldValue(453));
            assertEquals(integerArray, outList);
            assertSame(integerArray, outList);
            assertEquals(4, integerArray.size());
            Array anotherArray = new Array(type.getField("intarray").getDataType());
            anotherArray.add(new IntegerFieldValue(5324));
            Object o = struct.setFieldValue("intarray", anotherArray);
            assertEquals(integerArray, o);
            assertSame(integerArray, o);
            outList = (List) struct.getFieldValue("intarray");
            assertFalse(integerArray.equals(outList));
            assertEquals(anotherArray, outList);
            assertSame(anotherArray, outList);
        }
        {
            WeightedSet<StringFieldValue> strWs = new WeightedSet<>(type.getField("strws").getDataType());
            strWs.put(new StringFieldValue("banana"), 10);
            strWs.add(new StringFieldValue("apple"));
            assertEquals(2, strWs.size());
            Object o = struct.setFieldValue("strws", strWs);
            assertNull(o);
            assertEquals(2, strWs.size());
            WeightedSet<StringFieldValue> outWs = (WeightedSet<StringFieldValue>) struct.getFieldValue("strws");
            strWs.add(new StringFieldValue("poison"));
            strWs.put(new StringFieldValue("pie"), 599);
            assertEquals(strWs, outWs);
            assertSame(strWs, outWs);
            assertEquals(4, strWs.size());
            WeightedSet anotherWs = new WeightedSet(type.getField("strws").getDataType());
            anotherWs.add(new StringFieldValue("be bop"));
            o = struct.setFieldValue("strws", anotherWs);
            assertEquals(strWs, o);
            assertSame(strWs, o);
            outWs = (WeightedSet<StringFieldValue>) struct.getFieldValue("strws");

            System.out.println("OutWS " + outWs);
            System.out.println("StrWS " + strWs);

            assertFalse(strWs.equals(outWs));
            assertEquals(anotherWs, outWs);
            assertSame(anotherWs, outWs);
        }
    }

    @Test
    public void testSetUnknownType() {
        StructDataType type = new StructDataType("teststr");
        type.addField(new Field("int", 0, DataType.INT));

        Struct struct = new Struct(type);
        try {
            struct.setFieldValue(new Field("alien", DataType.STRING), new StringFieldValue("foo"));
            fail("Alien type worked");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().matches(".*No such field.*"));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCompareToDoesNotMutateStateBug6394548() {
        StructDataType type = new StructDataType("test");
        // NOTE: non-increasing ID order!
        type.addField(new Field("int", 2, DataType.INT));
        type.addField(new Field("flt", 1, DataType.FLOAT));
        type.addField(new Field("str", 0, DataType.STRING));

        Struct a = new Struct(type);
        a.setFieldValue("int", new IntegerFieldValue(123));
        a.setFieldValue("flt", new DoubleFieldValue(45.6));
        a.setFieldValue("str", new StringFieldValue("hello world"));
        Struct b = new Struct(type);
        b.setFieldValue("int", new IntegerFieldValue(100));
        b.setFieldValue("flt", new DoubleFieldValue(45.6));
        b.setFieldValue("str", new StringFieldValue("hello world"));

        String xmlBefore = a.toXml();
        int hashBefore = a.hashCode();

        assertEquals(1, a.compareTo(b));

        assertEquals(xmlBefore, a.toXml());
        assertEquals(hashBefore, a.hashCode());
    }

    @Test
    public void sortingFirstOrderedByNumberOfSetFields() {
        StructDataType type = new StructDataType("test");
        type.addField(new Field("int", DataType.INT));
        type.addField(new Field("flt", DataType.FLOAT));
        type.addField(new Field("str", DataType.STRING));

        Struct a = new Struct(type);
        a.setFieldValue("int", new IntegerFieldValue(123));
        Struct b = new Struct(type);

        assertTrue(b.compareTo(a) < 0);
        assertTrue(a.compareTo(b) > 0);
        assertEquals(0, b.compareTo(b));
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));

        b.setFieldValue("int", new IntegerFieldValue(123));

        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));

        b.setFieldValue("str", new StringFieldValue("hello world"));
        assertTrue(b.compareTo(a) > 0);
        assertTrue(a.compareTo(b) < 0);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }

    @Test
    public void sortingOrderIndependentOfValueInsertionOrder() {
        StructDataType type = new StructDataType("test");
        type.addField(new Field("int", DataType.INT));
        type.addField(new Field("flt", DataType.FLOAT));
        type.addField(new Field("str", DataType.STRING));

        Struct a = new Struct(type);
        a.setFieldValue("int", new IntegerFieldValue(123));
        a.setFieldValue("flt", new DoubleFieldValue(45.6));
        a.setFieldValue("str", new StringFieldValue("hello world"));
        Struct b = new Struct(type);
        b.setFieldValue("str", new StringFieldValue("hello world"));
        b.setFieldValue("flt", new DoubleFieldValue(45.6));
        b.setFieldValue("int", new IntegerFieldValue(123));

        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));

        b.setFieldValue("int", new IntegerFieldValue(122));
        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(a) < 0);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }

    @Test
    public void sortingOrderDependsOnTypeFieldOrderWhenNonEqual() {
        StructDataType type = new StructDataType("test");
        type.addField(new Field("int", DataType.INT));
        type.addField(new Field("intnotset", DataType.INT));
        type.addField(new Field("flt", DataType.FLOAT));
        type.addField(new Field("str", DataType.STRING));

        Struct a = new Struct(type);
        a.setFieldValue("int", new IntegerFieldValue(123));
        a.setFieldValue("flt", new DoubleFieldValue(45.6));
        Struct b = new Struct(type);
        b.setFieldValue("int", new IntegerFieldValue(123));
        b.setFieldValue("str", new StringFieldValue("hello world"));

        // a sorts before b as it has flt set which occurs before str in the type
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }
}
