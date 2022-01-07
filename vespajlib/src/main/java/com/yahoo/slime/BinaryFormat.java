// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.compress.Compressor;

/**
 * Class for serializing Slime data into binary format, or deserializing
 * the binary format into a Slime object.
 */
public class BinaryFormat {

    static long encode_zigzag(long x) {
        return ((x << 1) ^ (x >> 63)); // note ASR
    }

    static long decode_zigzag(long x) {
        return ((x >>> 1) ^ (-(x & 0x1))); // note LSR
    }

    static long encode_double(double x) {
        return Double.doubleToRawLongBits(x);
    }

    static double decode_double(long x) {
        return Double.longBitsToDouble(x);
    }

    static byte encode_type_and_meta(int type, int meta) {
        return (byte) ((meta << 3) | (type & 0x7));
    }

    static Type decode_type(byte type_and_meta) {
        return Type.asType(type_and_meta & 0x7);
    }

    static int decode_meta(byte type_and_meta) {
        return ((type_and_meta & 0xff) >>> 3);
    }

    /**
     * Take a Slime object and serialize it into binary format.
     * @param slime the object which is to be serialized.
     * @return a new byte array with just the encoded slime.
     **/
    public static byte[] encode(Slime slime) {
        return new BinaryEncoder().encode(slime).toArray();
    }

    /**
     * Take a Slime object and serialize it into binary format, and compresses it.
     * @param slime the object which is to be serialized.
     * @param  compressor the compressor to use.
     * @return a new byte array with just the encoded and compressed slime.
     **/
    public static Compressor.Compression encode_and_compress(Slime slime, Compressor compressor) {
        return new BinaryEncoder().encode(slime).compress(compressor);
    }

    /**
     * Take binary data and deserialize it into a Slime object.
     * The data is assumed to be the binary representation
     * as if obtained by a call to the @ref encode() method.
     *
     * If the binary data can't be deserialized without problems
     * the returned Slime object will instead only contain the
     * three fields "partial_result" (contains anything successfully
     * decoded before encountering problems), "offending_input"
     * (containing any data that could not be deserialized) and
     * "error_message" (a string describing the problem encountered).
     *
     * @param data the data to be deserialized.
     * @return a new Slime object constructed from the data.
     **/
    public static Slime decode(byte[] data) {
        BinaryDecoder decoder = new BinaryDecoder();
        return decoder.decode(data);
    }

    /**
     * Take binary data and deserialize it into a Slime object.
     * The data is assumed to be the binary representation
     * as if obtained by a call to the @ref encode() method.
     *
     * If the binary data can't be deserialized without problems
     * the returned Slime object will instead only contain the
     * three fields "partial_result" (contains anything successfully
     * decoded before encountering problems), "offending_input"
     * (containing any data that could not be deserialized) and
     * "error_message" (a string describing the problem encountered).
     *
     * @param data array containing the data to be deserialized.
     * @param offset where in the array to start deserializing.
     * @param length how many bytes the deserializer is allowed to consume.
     * @return a new Slime object constructed from the data.
     **/
    public static Slime decode(byte[] data, int offset, int length) {
        BinaryDecoder decoder = new BinaryDecoder();
        return decoder.decode(data, offset, length);
    }

}
