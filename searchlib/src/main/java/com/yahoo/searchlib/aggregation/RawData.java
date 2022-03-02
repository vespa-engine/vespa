// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

import java.util.Arrays;

/**
 * A byte array a a cloneable and comparable object. It also implements a sane {@link
 * #hashCode()} and {@link #toString()}.
 *
 * @author Simon Thoresen Hult
 */
public class RawData implements Cloneable, Comparable<RawData> {

    private byte[] data;

    /** Constructs an empty data object. */
    public RawData() {
        data = new byte[0];
    }

    /**
     * Constructs a raw data object that holds the given byte array.
     *
     * @param data the rank to set
     */
    public RawData(byte[] data) {
        setData(data);
    }

    /**
     * Serializes the content of this data into the given byte buffer.
     *
     * @param buf the buffer to serialize to
     */
    public void serialize(Serializer buf) {
        buf.putInt(null, data.length);
        buf.put(null, data);
    }

    /**
     * Deserializes the content for this data from the given byte buffer.
     *
     * @param buf the buffer to deserialize from
     */
    public void deserialize(Deserializer buf) {
        int len = buf.getInt(null);
        data = buf.getBytes(null, len);
    }

    /**
     * Returns the byte array that constitutes this data.
     *
     * @return the byte array
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Sets the byte array that constitutes this data. This does <b>not</b> copy the given array, it simply assigns
     * it to this.
     *
     * @param data the data to set
     * @return this, to allow chaining
     */
    public RawData setData(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data can not be null.");
        }
        this.data = data;
        return this;
    }

    @Override
    public int compareTo(RawData rhs) {
        return compare(data, rhs.data);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RawData)) {
            return false;
        }
        RawData rhs = (RawData)obj;
        if (!Arrays.equals(data, rhs.data)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "RawData(data = " + Arrays.toString(data) + ")";
    }

    @Override
    public Object clone() {
        return new RawData(Arrays.copyOf(data, data.length));
    }

    /**
     * Implements comparison of two byte arrays.
     *
     * @param lhs the left-hand-side of the comparison
     * @param rhs the right-hand-side of the comparison
     * @return the result of comparing the two byte arrays
     */
    public static int compare(byte[] lhs, byte[] rhs) {
        int cmp = 0;
        for (int i = 0, len = Math.min(lhs.length, rhs.length); (i < len) && (cmp == 0); i++) {
            int a = lhs[i] & 0xFF;
            int b = rhs[i] & 0xFF;
            cmp = a - b;
        }
        if (cmp == 0) {
            cmp = lhs.length - rhs.length;
        }
        return cmp;
    }

}
