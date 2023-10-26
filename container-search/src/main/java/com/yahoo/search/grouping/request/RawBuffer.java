// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.ArrayList;

/**
 * This class represents a buffer of byte values to be used as a backing buffer
 * for raw buckets.
 *
 * @author Ulf Lilleengen
 */
public class RawBuffer implements Comparable<RawBuffer>, Cloneable {

    private final ArrayList<Byte> buffer;

    /**
     * Create an empty buffer.
     */
    public RawBuffer() {
        this.buffer = new ArrayList<>();
    }

    /**
     * Create a buffer with initial content.
     *
     * @param buffer A buffer of values to be assigned this buffer.
     */
    public RawBuffer(ArrayList<Byte> buffer) {
        this.buffer = buffer;
    }

    /**
     * Create a buffer with initial content.
     *
     * @param bytes A buffer of bytes to be assigned this buffer.
     */
    public RawBuffer(byte[] bytes) {
        buffer = new ArrayList<>();
        put(bytes);
    }

    /**
     * Insert a byte value into this buffer.
     *
     * @param value The value to add to the buffer.
     * @return Reference to this.
     */
    public RawBuffer put(byte value) {
        buffer.add(value);
        return this;
    }

    /**
     * Insert an array of byte values into this buffer.
     *
     * @param values The array to add to the buffer.
     * @return Reference to this.
     */
    public RawBuffer put(byte[] values) {
        for (int i = 0; i < values.length; i++) {
            buffer.add(values[i]);
        }
        return this;
    }

    /**
     * Create a copy of data in the internal buffer.
     *
     * @return A copy of the data.
     */
    public byte[] getBytes() {
        byte[] ret = new byte[buffer.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = buffer.get(i);
        }
        return ret;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("{");
        for (int i = 0; i < buffer.size(); i++) {
            s.append(buffer.get(i));
            if (i < buffer.size() - 1) {
                s.append(",");
            }
        }
        s.append("}");
        return s.toString();
    }

    @Override
    public RawBuffer clone() {
        return new RawBuffer(new ArrayList<>(buffer));
    }

    @Override
    public int compareTo(RawBuffer rhs) {
        Byte[] my = buffer.toArray(new Byte[0]);
        Byte[] their = rhs.buffer.toArray(new Byte[0]);
        for (int i = 0; i < my.length && i < their.length; i++) {
            if (my[i] < their[i]) {
                return -1;
            } else if (my[i] > their[i]) {
                return 1;
            }
        }
        return Integer.compare(my.length, their.length);
    }

    @Override
    public int hashCode() {
        return buffer.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof RawBuffer)) return false;
        return (compareTo((RawBuffer)other) == 0);
    }

}
