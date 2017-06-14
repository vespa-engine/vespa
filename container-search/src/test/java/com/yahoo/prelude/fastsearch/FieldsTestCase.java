// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.search.result.NanNumber;
import com.yahoo.text.Utf8;

public class FieldsTestCase {

    ByteBuffer scratchSpace;
    FastHit contains;
    String fieldName = "field";

    @Before
    public void setUp() throws Exception {
        scratchSpace = ByteBuffer.allocate(10000);
        contains = new FastHit();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void testByte() {
        int s = scratchSpace.position();
        final byte value = (byte) 5;
        scratchSpace.put(value);
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new ByteField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new ByteField(fieldName).decode(scratchSpace, contains);
        assertEquals(Byte.valueOf(value), contains.getField(fieldName));
    }

    @Test
    public final void testData() {
        String value = "nalle";
        int s = scratchSpace.position();
        scratchSpace.putShort((short) value.length());
        scratchSpace.put(Utf8.toBytes(value));
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new DataField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new DataField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, contains.getField(fieldName).toString());
    }

    @Test
    public final void testDouble() {
        int s = scratchSpace.position();
        final double value = 5.0d;
        scratchSpace.putDouble(value);
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new DoubleField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new DoubleField(fieldName).decode(scratchSpace, contains);
        // slightly evil, but value is a exactly expressible as a double
        assertEquals(Double.valueOf(value), contains.getField(fieldName));
    }

    @Test
    public final void testFloat() {
        int s = scratchSpace.position();
        final float value = 5.0f;
        scratchSpace.putFloat(value);
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new FloatField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new FloatField(fieldName).decode(scratchSpace, contains);
        // slightly evil, but value is a exactly expressible as a float
        assertEquals(Float.valueOf(value), contains.getField(fieldName));
    }

    @Test
    public final void testInt64() {
        int s = scratchSpace.position();
        final long value = 5;
        scratchSpace.putLong(value);
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new Int64Field(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new Int64Field(fieldName).decode(scratchSpace, contains);
        assertEquals(Long.valueOf(value), contains.getField(fieldName));
    }

    @Test
    public final void testInteger() {
        int s = scratchSpace.position();
        final int value = 5;
        scratchSpace.putInt(value);
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new IntegerField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new IntegerField(fieldName).decode(scratchSpace, contains);
        assertEquals(Integer.valueOf(value), contains.getField(fieldName));
    }

    @Test
    public final void testNanExpressions() {
        byte b = ByteField.EMPTY_VALUE;
        short s = ShortField.EMPTY_VALUE;
        int i = IntegerField.EMPTY_VALUE;
        long l = Int64Field.EMPTY_VALUE;
        assertFalse(((short) b) == s);
        assertFalse(((int) s) == i);
        assertFalse(((long) i) == l);
        scratchSpace.put(b);
        scratchSpace.putShort(s);
        scratchSpace.putInt(i);
        scratchSpace.putLong(l);
        scratchSpace.putFloat(Float.NaN);
        scratchSpace.putDouble(Double.NaN);
        scratchSpace.flip();
        final String bytename = fieldName + "_b";
        new ByteField(bytename).decode(scratchSpace, contains);
        final String shortname = fieldName + "_s";
        new ShortField(shortname).decode(scratchSpace, contains);
        final String intname = fieldName + "_i";
        new IntegerField(intname).decode(scratchSpace, contains);
        final String longname = fieldName + "_l";
        new Int64Field(longname).decode(scratchSpace, contains);
        final String floatname = fieldName + "_f";
        new FloatField(floatname).decode(scratchSpace, contains);
        final String doublename = fieldName + "_d";
        new DoubleField(doublename).decode(scratchSpace, contains);
        assertSame(NanNumber.NaN, contains.getField(bytename));
        assertSame(NanNumber.NaN, contains.getField(shortname));
        assertSame(NanNumber.NaN, contains.getField(intname));
        assertSame(NanNumber.NaN, contains.getField(longname));
        assertSame(NanNumber.NaN, contains.getField(floatname));
        assertSame(NanNumber.NaN, contains.getField(doublename));
    }

    @Test
    public final void testJSON() {
        String value = "{1: 2}";
        int s = scratchSpace.position();
        scratchSpace.putInt(value.length());
        scratchSpace.put(Utf8.toBytes(value));
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new JSONField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new JSONField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, ((JSONString) contains.getField(fieldName)).getContent());
    }

    @Test
    public final void testLongdata() {
        String value = "nalle";
        int s = scratchSpace.position();
        scratchSpace.putInt(value.length());
        scratchSpace.put(Utf8.toBytes(value));
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new LongdataField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new LongdataField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, contains.getField(fieldName).toString());
    }

    @Test
    public final void testLongstring() {
        String value = "nalle";
        int s = scratchSpace.position();
        scratchSpace.putInt(value.length());
        scratchSpace.put(Utf8.toBytes(value));
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new LongstringField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new LongstringField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, contains.getField(fieldName));
    }

    @Test
    public final void testShort() {
        int s = scratchSpace.position();
        final short value = 5;
        scratchSpace.putShort(value);
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new ShortField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new ShortField(fieldName).decode(scratchSpace, contains);
        assertEquals(Short.valueOf(value), contains.getField(fieldName));
    }

    @Test
    public final void testString() {
        String value = "nalle";
        int s = scratchSpace.position();
        scratchSpace.putShort((short) value.length());
        scratchSpace.put(Utf8.toBytes(value));
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new StringField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new StringField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, contains.getField(fieldName));
    }

    @Test
    public final void testXML() {
        String value = "nalle";
        int s = scratchSpace.position();
        scratchSpace.putInt(value.length());
        scratchSpace.put(Utf8.toBytes(value));
        int l = scratchSpace.position();
        scratchSpace.flip();
        assertEquals(l, new XMLField(fieldName).getLength(scratchSpace));
        scratchSpace.position(s);
        new XMLField(fieldName).decode(scratchSpace, contains);
        assertTrue(contains.getField(fieldName).getClass() == XMLString.class);
        assertEquals(value, contains.getField(fieldName).toString());
    }

    @Test
    public final void testCompressionLongdata() {
        String value = "000000000000000000000000000000000000000000000000000000000000000";
        byte[] raw = Utf8.toBytesStd(value);
        byte[] output = new byte[raw.length * 2];
        Deflater compresser = new Deflater();
        compresser.setInput(raw);
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);
        compresser.end();
        scratchSpace.putInt((compressedDataLength + 4) | (1 << 31));
        scratchSpace.putInt(raw.length);
        scratchSpace.put(output, 0, compressedDataLength);
        scratchSpace.flip();
        assertTrue(new LongdataField(fieldName).isCompressed(scratchSpace));
        new LongdataField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, contains.getField(fieldName).toString());
    }

    @Test
    public final void testCompressionJson() {
        String value = "{0:000000000000000000000000000000000000000000000000000000000000000}";
        byte[] raw = Utf8.toBytesStd(value);
        byte[] output = new byte[raw.length * 2];
        Deflater compresser = new Deflater();
        compresser.setInput(raw);
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);
        compresser.end();
        scratchSpace.putInt((compressedDataLength + 4) | (1 << 31));
        scratchSpace.putInt(raw.length);
        scratchSpace.put(output, 0, compressedDataLength);
        scratchSpace.flip();
        assertTrue(new JSONField(fieldName).isCompressed(scratchSpace));
        new JSONField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, ((JSONString) contains.getField(fieldName)).getContent());
    }

    @Test
    public final void testCompressionLongstring() {
        String value = "000000000000000000000000000000000000000000000000000000000000000";
        byte[] raw = Utf8.toBytesStd(value);
        byte[] output = new byte[raw.length * 2];
        Deflater compresser = new Deflater();
        compresser.setInput(raw);
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);
        compresser.end();
        scratchSpace.putInt((compressedDataLength + 4) | (1 << 31));
        scratchSpace.putInt(raw.length);
        scratchSpace.put(output, 0, compressedDataLength);
        scratchSpace.flip();
        assertTrue(new LongstringField(fieldName).isCompressed(scratchSpace));
        new LongstringField(fieldName).decode(scratchSpace, contains);
        assertEquals(value, contains.getField(fieldName));
    }

    @Test
    public final void testCompressionXml() {
        String value = "000000000000000000000000000000000000000000000000000000000000000";
        byte[] raw = Utf8.toBytesStd(value);
        byte[] output = new byte[raw.length * 2];
        Deflater compresser = new Deflater();
        compresser.setInput(raw);
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);
        compresser.end();
        scratchSpace.putInt((compressedDataLength + 4) | (1 << 31));
        scratchSpace.putInt(raw.length);
        scratchSpace.put(output, 0, compressedDataLength);
        scratchSpace.flip();
        assertTrue(new XMLField(fieldName).isCompressed(scratchSpace));
        new XMLField(fieldName).decode(scratchSpace, contains);
        assertTrue(contains.getField(fieldName).getClass() == XMLString.class);
        assertEquals(value, contains.getField(fieldName).toString());

    }

    @Test
    public final void checkLengthFieldLengths() {
        assertEquals(2, new DataField(fieldName).sizeOfLength());
        assertEquals(4, new JSONField(fieldName).sizeOfLength());
        assertEquals(4, new LongdataField(fieldName).sizeOfLength());
        assertEquals(4, new LongstringField(fieldName).sizeOfLength());
        assertEquals(2, new StringField(fieldName).sizeOfLength());
        assertEquals(4, new XMLField(fieldName).sizeOfLength());
    }
}
