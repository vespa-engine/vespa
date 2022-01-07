// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author baldersheim
 */
public class BufferSerializer implements Serializer, Deserializer {

    protected GrowableByteBuffer buf;

    public BufferSerializer(GrowableByteBuffer buf) { this.buf = buf; }
    public BufferSerializer(ByteBuffer buf) { this(new GrowableByteBuffer(buf)); }
    public BufferSerializer(byte [] buf) { this(ByteBuffer.wrap(buf)); }
    public BufferSerializer() { this(new GrowableByteBuffer()); }
    public static BufferSerializer wrap(byte [] buf)    { return new BufferSerializer(buf); }
    public final GrowableByteBuffer getBuf()            { return buf; }
    protected final void setBuf(GrowableByteBuffer buf) { this.buf = buf; }
    public Serializer putByte(FieldBase field, byte value)        { buf.put(value); return this; }
    public Serializer putShort(FieldBase field, short value)      { buf.putShort(value); return this; }
    public Serializer putInt(FieldBase field, int value)          { buf.putInt(value); return this; }
    public Serializer putLong(FieldBase field, long value)        { buf.putLong(value); return this; }
    public Serializer putFloat(FieldBase field, float value)      { buf.putFloat(value); return this; }
    public Serializer putDouble(FieldBase field, double value)    { buf.putDouble(value); return this; }
    public Serializer put(FieldBase field, byte[] value)         { buf.put(value); return this; }
    public Serializer put(FieldBase field, String value) {
        byte [] utf8 = createUTF8CharArray(value);
        putInt(null, utf8.length+1);
        put(null, utf8);
        putByte(null, (byte) 0);
        return this;
    }
    public Serializer put(FieldBase field, ByteBuffer value)         { buf.put(value); return this; }
    public Serializer putInt1_4Bytes(FieldBase field, int value)     { buf.putInt1_4Bytes(value); return this; }
    public Serializer putInt2_4_8Bytes(FieldBase field, long value)  { buf.putInt2_4_8Bytes(value); return this; }
    public int position()    { return buf.position(); }
    public ByteOrder order() { return buf.order(); }
    public void position(int pos) { buf.position(pos); }
    public void order(ByteOrder v) { buf.order(v); }
    public void flip() { buf.flip(); }

    public byte getByte(FieldBase field)            { return buf.getByteBuffer().get(); }
    public short getShort(FieldBase field)          { return buf.getByteBuffer().getShort(); }
    public int getInt(FieldBase field)              { return buf.getByteBuffer().getInt(); }
    public long getLong(FieldBase field)            { return buf.getByteBuffer().getLong(); }
    public float getFloat(FieldBase field)          { return buf.getByteBuffer().getFloat(); }
    public double getDouble(FieldBase field)        { return buf.getByteBuffer().getDouble(); }
    public byte [] getBytes(FieldBase field, int length) {
        if (buf.remaining() < length) {
            throw new IllegalArgumentException("Wanted " + length + " bytes, but I only had " + buf.remaining());
        }
        byte [] bbuf =new byte [length];
        buf.getByteBuffer().get(bbuf);
        return bbuf;
    }
    public String getString(FieldBase field)        {
        int length = getInt(null);
        byte[] stringArray = new byte[length-1];
        buf.get(stringArray);
        getByte(null);
        return Utf8.toString(stringArray);
    }
    public int getInt1_4Bytes(FieldBase field)      { return buf.getInt1_4Bytes(); }
    public int getInt1_2_4Bytes(FieldBase field)    { return buf.getInt1_2_4Bytes(); }
    public long getInt2_4_8Bytes(FieldBase field)   { return buf.getInt2_4_8Bytes(); }
    public int remaining()           { return buf.remaining(); }

    public static byte[] createUTF8CharArray(String input) {
        if (input == null || input.length() < 1) {
            return new byte[0];
        }
        return Utf8.toBytes(input);
    }

}

