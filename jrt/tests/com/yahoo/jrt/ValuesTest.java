// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ValuesTest {

    @org.junit.Test
    public void testEmpty() {
        Values src = new Values();
        assertEquals(src.bytes(), 4);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), 4);

        Values dst = new Values();
        dst.decode(buf);
        assertEquals(dst.bytes(), 4);
    }

    void checkSingleValue(Values v, byte type, int bytes) {
        assertEquals(v.size(), 1);
        assertEquals(v.get(0).type(), type);
        assertEquals(v.bytes(), bytes);
    }

    @org.junit.Test
    public void testInt8() {
        int byteSize = 4 + 1 + 1;
        Values src = new Values();
        src.add(new Int8Value((byte)1));
        checkSingleValue(src, Value.INT8, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT8, byteSize);
        assertEquals(dst.get(0).asInt8(), (byte)1);
    }

    @org.junit.Test
    public void testInt8Array() {
        int byteSize = 4 + 1 + 4 + 4;
        Values src = new Values();
        byte[] val = { 1, 2, 3, 4 };
        src.add(new Int8Array(val));
        checkSingleValue(src, Value.INT8_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT8_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asInt8Array(), val));
    }

    @org.junit.Test
    public void testInt16() {
        int byteSize = 4 + 1 + 2;
        Values src = new Values();
        src.add(new Int16Value((short)2));
        checkSingleValue(src, Value.INT16, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT16, byteSize);
        assertEquals(dst.get(0).asInt16(), (short)2);
    }

    @org.junit.Test
    public void testInt16Array() {
        int byteSize = 4 + 1 + 4 + 4 * 2;
        Values src = new Values();
        short[] val = { 2, 4, 6, 8 };
        src.add(new Int16Array(val));
        checkSingleValue(src, Value.INT16_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT16_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asInt16Array(), val));
    }

    @org.junit.Test
    public void testInt32() {
        int byteSize = 4 + 1 + 4;
        Values src = new Values();
        src.add(new Int32Value(4));
        checkSingleValue(src, Value.INT32, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT32, byteSize);
        assertEquals(dst.get(0).asInt32(), 4);
    }

    @org.junit.Test
    public void testInt32Array() {
        int byteSize = 4 + 1 + 4 + 4 * 4;
        Values src = new Values();
        int[] val = { 4, 8, 12, 16 };
        src.add(new Int32Array(val));
        checkSingleValue(src, Value.INT32_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT32_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asInt32Array(), val));
    }

    @org.junit.Test
    public void testInt64() {
        int byteSize = 4 + 1 + 8;
        Values src = new Values();
        src.add(new Int64Value(8));
        checkSingleValue(src, Value.INT64, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT64, byteSize);
        assertEquals(dst.get(0).asInt64(), 8);
    }

    @org.junit.Test
    public void testInt64Array() {
        int byteSize = 4 + 1 + 4 + 4 * 8;
        Values src = new Values();
        long[] val = { 8, 16, 24, 32 };
        src.add(new Int64Array(val));
        checkSingleValue(src, Value.INT64_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.INT64_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asInt64Array(), val));
    }

    @org.junit.Test
    public void testFloat() {
        int byteSize = 4 + 1 + 4;
        Values src = new Values();
        src.add(new FloatValue((float)2.5));
        checkSingleValue(src, Value.FLOAT, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.FLOAT, byteSize);
        assertTrue(dst.get(0).asFloat() == (float)2.5);
    }

    @org.junit.Test
    public void testFloatArray() {
        int byteSize = 4 + 1 + 4 + 4 * 4;
        Values src = new Values();
        float[] val = { 1.5f, 2.0f, 2.5f, 3.0f };
        src.add(new FloatArray(val));
        checkSingleValue(src, Value.FLOAT_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.FLOAT_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asFloatArray(), val));
    }

    @org.junit.Test
    public void testDouble() {
        int byteSize = 4 + 1 + 8;
        Values src = new Values();
        src.add(new DoubleValue(3.75));
        checkSingleValue(src, Value.DOUBLE, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.DOUBLE, byteSize);
        assertTrue(dst.get(0).asDouble() == 3.75);
    }

    @org.junit.Test
    public void testDoubleArray() {
        int byteSize = 4 + 1 + 4 + 4 * 8;
        Values src = new Values();
        double[] val = { 1.25, 1.50, 1.75, 2.00 };
        src.add(new DoubleArray(val));
        checkSingleValue(src, Value.DOUBLE_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.DOUBLE_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asDoubleArray(), val));
    }

    @org.junit.Test
    public void testData() {
        int byteSize = 4 + 1 + 4 + 4;
        Values src = new Values();
        byte[] val = { 1, 2, 3, 4 };
        src.add(new DataValue(val));
        checkSingleValue(src, Value.DATA, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.DATA, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asData(), val));
    }

    @org.junit.Test
    public void testDataArray() {
        int byteSize = 4 + 1 + 4 + 4 * (4 + 4);
        Values src = new Values();
        byte[][] val = {{ 1, 0, 1, 0 },
                        { 0, 2, 0, 2 },
                        { 3, 0, 3, 0 },
                        { 0, 4, 0, 4 }};
        src.add(new DataArray(val));
        checkSingleValue(src, Value.DATA_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.DATA_ARRAY, byteSize);
        assertTrue(Arrays.equals(dst.get(0).asDataArray()[0], val[0]));
        assertTrue(Arrays.equals(dst.get(0).asDataArray()[1], val[1]));
        assertTrue(Arrays.equals(dst.get(0).asDataArray()[2], val[2]));
        assertTrue(Arrays.equals(dst.get(0).asDataArray()[3], val[3]));
    }

    @org.junit.Test
    public void testString1() {
        int byteSize = 4 + 1 + 4 + 4;
        Values src = new Values();
        String val = "test";
        src.add(new StringValue(val));
        checkSingleValue(src, Value.STRING, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.STRING, byteSize);
        assertTrue(dst.get(0).asString().equals("test"));
    }

    @org.junit.Test
    public void testString2() {
        int byteSize = 4 + 1 + 4 + 7;
        Values src = new Values();
        String val = "H" + ((char)229) + "vard";
        src.add(new StringValue(val));
        checkSingleValue(src, Value.STRING, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        byte right[] = new byte[] { 0, 0, 0, 1, 's',
                                    0, 0, 0, 7, 'H',
                                    (byte)(0xC0 | (0xE5 >> 6)),
                                    (byte)(0x80 | (0xE5 & 0x3F)),
                                    'v', 'a', 'r', 'd'
        };
        for (int ii = 0; ii < buf.remaining(); ++ii) {
            assertEquals(buf.get(ii), right[ii]);
        }

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.STRING, byteSize);
        assertTrue(dst.get(0).asString().equals("H\u00E5vard"));
    }

    @org.junit.Test
    public void testStringArray() {
        int byteSize = 4 + 1 + 4 + 4 * 4 + 3 + 3 + 5 + 4;
        Values src = new Values();
        String[] val = { "one", "two", "three", "four" };
        src.add(new StringArray(val));
        checkSingleValue(src, Value.STRING_ARRAY, byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        checkSingleValue(src, Value.STRING_ARRAY, byteSize);
        assertTrue(dst.get(0).asStringArray()[0].equals("one"));
        assertTrue(dst.get(0).asStringArray()[1].equals("two"));
        assertTrue(dst.get(0).asStringArray()[2].equals("three"));
        assertTrue(dst.get(0).asStringArray()[3].equals("four"));
    }

    @org.junit.Test
    public void testAllValues() {
        int byteSize =
            4 + 16                       // typestring
            + 1                          // int8
            + 4 + 4                      // int8 array
            + 2                          // int16
            + 4 + 4 * 2                  // int16 array
            + 4                          // int32
            + 4 + 4 * 4                  // int32 array
            + 8                          // int64
            + 4 + 4 * 8                  // int64 array
            + 4                          // float
            + 4 + 4 * 4                  // float array
            + 8                          // double
            + 4 + 4 * 8                  // double array
            + 4 + 4                      // data
            + 4 + 4 * 4 + 4 + 4 + 4 + 4  // data array
            + 4 + 4                      // string
            + 4 + 4 * 4 + 3 + 3 + 5 + 4; // string array

        byte[] dataValue = { 1, 2, 3, 4 };
        byte[] int8Array = { 1, 2, 3, 4 };
        short[] int16Array = { 2, 4, 6, 8 };
        int[] int32Array = { 4, 8, 12, 16 };
        long[] int64Array = { 8, 16, 24, 32 };
        float[] floatArray = { 1.5f, 2.0f, 2.5f, 3.0f };
        double[] doubleArray = { 1.25, 1.50, 1.75, 2.00 };
        byte[][] dataArray = {{ 1, 0, 1, 0 },
                              { 0, 2, 0, 2 },
                              { 3, 0, 3, 0 },
                              { 0, 4, 0, 4 }};
        String[] stringArray = { "one", "two", "three", "four" };

        Values src = new Values();
        src.add(new Int8Value((byte)1));
        src.add(new Int8Array(int8Array));
        src.add(new Int16Value((short)2));
        src.add(new Int16Array(int16Array));
        src.add(new Int32Value(4));
        src.add(new Int32Array(int32Array));
        src.add(new Int64Value(8));
        src.add(new Int64Array(int64Array));
        src.add(new FloatValue(2.5f));
        src.add(new FloatArray(floatArray));
        src.add(new DoubleValue(3.75));
        src.add(new DoubleArray(doubleArray));
        src.add(new DataValue(dataValue));
        src.add(new DataArray(dataArray));
        src.add(new StringValue("test"));
        src.add(new StringArray(stringArray));
        assertEquals(src.size(), 16);
        assertEquals(src.bytes(), byteSize);

        ByteBuffer buf = ByteBuffer.allocate(src.bytes());
        src.encode(buf);
        buf.flip();
        assertEquals(buf.remaining(), byteSize);

        Values dst = new Values();
        dst.decode(buf);
        assertEquals(dst.get(0).asInt8(), (byte)1);
        assertTrue(Arrays.equals(dst.get(1).asInt8Array(), int8Array));
        assertEquals(dst.get(2).asInt16(), (short)2);
        assertTrue(Arrays.equals(dst.get(3).asInt16Array(), int16Array));
        assertEquals(dst.get(4).asInt32(), 4);
        assertTrue(Arrays.equals(dst.get(5).asInt32Array(), int32Array));
        assertEquals(dst.get(6).asInt64(), 8);
        assertTrue(Arrays.equals(dst.get(7).asInt64Array(), int64Array));
        assertTrue(dst.get(8).asFloat() == (float)2.5);
        assertTrue(Arrays.equals(dst.get(9).asFloatArray(), floatArray));
        assertTrue(dst.get(10).asDouble() == 3.75);
        assertTrue(Arrays.equals(dst.get(11).asDoubleArray(), doubleArray));
        assertTrue(Arrays.equals(dst.get(12).asData(), dataValue));
        assertTrue(Arrays.equals(dst.get(13).asDataArray()[0], dataArray[0]));
        assertTrue(Arrays.equals(dst.get(13).asDataArray()[1], dataArray[1]));
        assertTrue(Arrays.equals(dst.get(13).asDataArray()[2], dataArray[2]));
        assertTrue(Arrays.equals(dst.get(13).asDataArray()[3], dataArray[3]));
        assertTrue(dst.get(14).asString().equals("test"));
        assertTrue(dst.get(15).asStringArray()[0].equals("one"));
        assertTrue(dst.get(15).asStringArray()[1].equals("two"));
        assertTrue(dst.get(15).asStringArray()[2].equals("three"));
        assertTrue(dst.get(15).asStringArray()[3].equals("four"));
    }

}
