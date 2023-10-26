// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.serialization;

import com.yahoo.search.predicate.PredicateIndex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Misc utility functions to help serialization of {@link PredicateIndex}.
 *
 * @author bjorncs
 */
public class SerializationHelper {

    public static void writeIntArray(int[] array, DataOutputStream out) throws IOException {
        out.writeInt(array.length);
        for (int v : array) {
            out.writeInt(v);
        }
    }

    public static int[] readIntArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        int[] array = new int[length];
        for (int i = 0; i < length; i++) {
            array[i] = in.readInt();
        }
        return array;
    }

    public static void writeByteArray(byte[] array, DataOutputStream out) throws IOException {
        out.writeInt(array.length);
        for (int v : array) {
            out.writeByte(v);
        }
    }

    public static byte[] readByteArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] array = new byte[length];
        for (int i = 0; i < length; i++) {
            array[i] = in.readByte();
        }
        return array;
    }

    public static void writeLongArray(long[] array, DataOutputStream out) throws IOException {
        out.writeInt(array.length);
        for (long v : array) {
            out.writeLong(v);
        }
    }

    public static long[] readLongArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        long[] array = new long[length];
        for (int i = 0; i < length; i++) {
            array[i] = in.readLong();
        }
        return array;
    }

    public static void writeShortArray(short[] array, DataOutputStream out) throws IOException {
        out.writeInt(array.length);
        for (short v : array) {
            out.writeShort(v);
        }
    }

    public static short[] readShortArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        short[] array = new short[length];
        for (int i = 0; i < length; i++) {
            array[i] = in.readShort();
        }
        return array;
    }

}
