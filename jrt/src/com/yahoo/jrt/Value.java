// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.text.Utf8Array;

import java.nio.ByteBuffer;


/**
 * <p>A single value that may be either a parameter or a return value
 * associated with a {@link Request}. Multiple values are bundled
 * together with the {@link Values} class. The value type identifiers
 * are defined by the RPC protocol. Each identifier matches the value
 * of an ASCII character (listed after the Java class for the type).</p>
 *
 * <p>Most Value subclasses that are constructed from a Java array
 * will not copy the array. This enables the same data to back
 * multiple Value objects, but it also means that the application
 * should be careful not to change the backing data under the feet of
 * a Value object.</p>
 **/
public abstract class Value
{
    /** type identifier for {@link Int8Value} (b) **/
    public static final byte INT8         = 'b';

    /** type identifier for {@link Int8Array} (B) **/
    public static final byte INT8_ARRAY   = 'B';

    /** type identifier for {@link Int16Value} (h) **/
    public static final byte INT16        = 'h';

    /** type identifier for {@link Int16Array} (H) **/
    public static final byte INT16_ARRAY  = 'H';

    /** type identifier for {@link Int32Value} (i) **/
    public static final byte INT32        = 'i';

    /** type identifier for {@link Int32Array} (I) **/
    public static final byte INT32_ARRAY  = 'I';

    /** type identifier for {@link Int64Value} (l) **/
    public static final byte INT64        = 'l';

    /** type identifier for {@link Int64Array} (L) **/
    public static final byte INT64_ARRAY  = 'L';

    /** type identifier for {@link FloatValue} (f) **/
    public static final byte FLOAT        = 'f';

    /** type identifier for {@link FloatArray} (F) **/
    public static final byte FLOAT_ARRAY  = 'F';

    /** type identifier for {@link DoubleValue} (d) **/
    public static final byte DOUBLE       = 'd';

    /** type identifier for {@link DoubleArray} (D) **/
    public static final byte DOUBLE_ARRAY = 'D';

    /** type identifier for {@link StringValue} (s) **/
    public static final byte STRING       = 's';

    /** type identifier for {@link StringArray} (S) **/
    public static final byte STRING_ARRAY = 'S';

    /** type identifier for {@link DataValue} (x) **/
    public static final byte DATA         = 'x';

    /** type identifier for {@link DataArray} (X) **/
    public static final byte DATA_ARRAY   = 'X';

    /**
     * Obtain the type identifier for this value
     *
     * @return type identifier
     **/
    public abstract byte type();

    /**
     * Obtain the number of entries stored in this value. This is 1
     * for basic data types and the size of the array for array types.
     *
     * @return the number of entries stored in this value
     **/
    public abstract int count();

    /**
     * Determine the number of bytes needed to store this value when
     * encoded into a buffer
     *
     * @return number of bytes needed for encoding this value
     **/
    abstract int bytes();

    /**
     * Encode this value into the given buffer
     *
     * @param dst where to encode this value
     **/
    abstract void encode(ByteBuffer dst);

    /**
     * Decode a value from the given buffer. This method also acts as
     * a factory for value objects
     *
     * @return the decoded value
     * @param type value type identifier
     * @param src where the value is stored
     * @throws IllegalArgumentException if the given type identifier is illegal
     **/
    static Value decode(byte type, ByteBuffer src) {
        switch (type) {
        case INT8:         return new Int8Value(src);
        case INT8_ARRAY:   return new Int8Array(src);
        case INT16:        return new Int16Value(src);
        case INT16_ARRAY:  return new Int16Array(src);
        case INT32:        return new Int32Value(src);
        case INT32_ARRAY:  return new Int32Array(src);
        case INT64:        return new Int64Value(src);
        case INT64_ARRAY:  return new Int64Array(src);
        case FLOAT:        return new FloatValue(src);
        case FLOAT_ARRAY:  return new FloatArray(src);
        case DOUBLE:       return new DoubleValue(src);
        case DOUBLE_ARRAY: return new DoubleArray(src);
        case STRING:       return new StringValue(src);
        case STRING_ARRAY: return new StringArray(src);
        case DATA:         return new DataValue(src);
        case DATA_ARRAY:   return new DataArray(src);
        }
        throw new IllegalArgumentException();
    }

    /**
     * Interpret this value as a {@link Int8Value} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int8Value}
     **/
    public byte     asInt8()        { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int8Array} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int8Array}
     **/
    public byte[]   asInt8Array()   { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int16Value} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int16Value}
     **/
    public short    asInt16()       { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int16Array} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int16Array}
     **/
    public short[]  asInt16Array()  { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int32Value} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int32Value}
     **/
    public int      asInt32()       { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int32Array} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int32Array}
     **/
    public int[]    asInt32Array()  { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int64Value} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int64Value}
     **/
    public long     asInt64()       { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link Int64Array} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Int64Array}
     **/
    public long[]   asInt64Array()  { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link FloatValue} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link FloatValue}
     **/
    public float    asFloat()       { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link FloatArray} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link FloatArray}
     **/
    public float[]  asFloatArray()  { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link DoubleValue} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link DoubleValue}
     **/
    public double   asDouble()      { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link DoubleArray} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link DoubleArray}
     **/
    public double[] asDoubleArray() { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link StringValue} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link StringValue}
     **/
    public String   asString()      { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link StringValue} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link Utf8Array}
     **/
    public Utf8Array asUtf8Array() { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link StringArray} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link StringArray}
     **/
    public String[] asStringArray() { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link DataValue} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link DataValue}
     **/
    public byte[]   asData()        { throw new ClassCastException(); }

    /**
     * Interpret this value as a {@link DataArray} and return the
     * contents as an appropriate Java type
     *
     * @return the value contained in this object as a Java type
     * @throws ClassCastException if this is not a {@link DataArray}
     **/
    public byte[][] asDataArray()   { throw new ClassCastException(); }

    /** Force a proper toString */
    public abstract @Override String toString();

}
