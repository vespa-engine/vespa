// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;


/**
 * A sequence of values used to represent parameters and return values
 * associated with a {@link Request}. The individual values are
 * represented by {@link Value} objects.
 **/
public class Values
{
    private List<Value> values = new ArrayList<Value>(16);

    /**
     * Check whether the values stored in this object satisfies the
     * given type string.
     *
     * @return true if this value sequence satisfies 'types'
     * @param types type string
     **/
    public boolean satisfies(String types) {
        int off = 0;
        int len = Math.min(types.length(), size());
        while (off < len && types.charAt(off) == get(off).type()) {
            off++;
        }
        return ((off == types.length() && off == size()) ||
                (off + 1 == types.length() && types.charAt(off) == '*'));
    }

    /**
     * Create an empty sequence of values
     **/
    public Values() {
    }

    /**
     * Create a sequence of values by decoding them from the given
     * buffer
     *
     * @param src buffer containing a contained value sequence
     **/
    Values(ByteBuffer src) {
        decode(src);
    }

    /**
     * Add a value to the end of the sequence
     *
     * @return this, to enable chaining
     * @param value the value to add
     **/
    public Values add(Value value) {
        values.add(value);
        return this;
    }

    /**
     * Obtain a specific value from this sequence
     *
     * @return a value from this sequence
     * @param idx the index of the value to obtain
     **/
    public Value get(int idx) {
        return values.get(idx);
    }

    /**
     * Obtain the number of values in this sequence
     *
     * @return the number of values in this sequence
     **/
    public int size() {
        return values.size();
    }

    /**
     * Determine the number of bytes needed to store this value
     * sequence when encoded into a buffer
     *
     * @return number of bytes needed for encoding this value sequence
     **/
    int bytes() {
        int bytes = 4 + values.size();
        for (int i = 0; i < values.size(); i++) {
            bytes += get(i).bytes();
        }
        return bytes;
    }

    /**
     * Encode this value sequence into the given buffer
     *
     * @param dst where to encode this value sequence
     **/
    void encode(ByteBuffer dst) {
        byte[] types = new byte[values.size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = get(i).type();
        }
        dst.putInt(types.length);
        dst.put(types);
        for (int i = 0; i < types.length; i++) {
            get(i).encode(dst);
        }
    }

    /**
     * Decode a value sequence from the given buffer into this object
     *
     * @param src where the value sequence is stored
     **/
    void decode(ByteBuffer src) {
        values.clear();
        int cnt = src.getInt();
        byte[] types = new byte[cnt];
        src.get(types);
        for (int i = 0; i < cnt; i++) {
            values.add(Value.decode(types[i], src));
        }
    }

    @Override
    public String toString() {
        if (values.size()==0) return "";
        if (values.size()==1) return values.get(0).toString();
        StringBuffer buffer=new StringBuffer();
        for (int i=0; i<values.size(); i++) {
            buffer.append(values.get(i).toString());
            if (i<values.size()-1)
                buffer.append(",");
        }
        return buffer.toString();
    }

}
