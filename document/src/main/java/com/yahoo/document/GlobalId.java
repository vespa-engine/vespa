// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.collections.MD5;
import com.yahoo.document.idstring.IdString;
import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Serializer;

import java.util.Arrays;

/**
 * Implements an incredibly light-weight version of the document global id. There is a lot of functionality in the C++
 * version of this that is missing. However, this should be sufficient for now.
 *
 * This is immutable (by contract - not enforcable due to exposing the raw byte array).
 *
 * @author Simon Thoresen
 */
public class GlobalId implements Comparable {

    /**
     * The number of bytes in a global id. This must match the C++ constant in "document/base/globalid.h".
     */
    public static final int LENGTH = 12;

    // The raw bytes that constitutes this global id.
    private final byte[] raw;

    /**
     * Constructs a new global id by copying the content of the given raw byte array.
     *
     * @param raw The array to copy.
     */
    public GlobalId(byte[] raw) {
        this.raw = new byte [12];
        int len = Math.min(LENGTH, raw.length);
        System.arraycopy(raw, 0, this.raw, 0, len);
    }

    /**
     * Constructs a new global id from a document id string.
     *
     * @param id The document id to derive from.
     */
    public GlobalId(IdString id) {
        byte [] raw = MD5.md5.get().digest(id.toUtf8().wrap().array());
        long location = id.getLocation();
        this.raw = new byte [LENGTH];
        for (int i = 0; i < 4; ++i) {
            this.raw[i] = (byte)((location >> (8 * i)) & 0xFF);
        }
        for (int i=4; i < LENGTH; i++) {
            this.raw[i] = raw[i];
        }
    }

    /**
     * Constructs a global id by deserializing content from the given byte buffer.
     *
     * @param buf The buffer to deserialize from.
     */
    public GlobalId(Deserializer buf) {
        raw = buf.getBytes(null, LENGTH);
    }

    /**
     * Serializes the content of this global id into the given byte buffer.
     *
     * @param buf The buffer to serialize to.
     */
    public void serialize(Serializer buf) {
        buf.put(null, raw);
    }

    /**
     * Returns the raw byte array that constitutes this global id.
     * The returned value MUST NOT be modified.
     */
    public byte[] getRawId() {
        return raw;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(raw);
    }

    public BucketId toBucketId() {
        /*
         * Explanation time: since Java was designed so mankind could suffer,
         * shift ops on bytes have an implicit int conversion with sign-extend.
         * When a byte is negative, you end up with an int/long with a 0xFFFFFF
         * prefix, in turn causing your other friendly bitwise ORs to act
         * pretty far from what was originally intended.
         * To get around this, we explicitly sign extend before the compiler can
         * do so for us and make sure to OR away any sign extensions.
         */
        long location = ((long)raw[0] & 0xFF)
                | (((long)raw[1] & 0xFF) << 8)
                | (((long)raw[2] & 0xFF) << 16)
                | (((long)raw[3] & 0xFF) << 24);
        long md5 = 0;
        for (int i = 4, j = 0; i < LENGTH; i++, j += 8) {
            md5 |= ((long)raw[i] & 0xFF) << j;
        }
        // Drumroll: this is why 'location' is of type long. Otherwise, the
        // ORing would sign-extend it and cause havoc when its MSB is set.
        long rawBucketId = (md5 & 0xFFFFFFFF00000000L) | location;
        return new BucketId(58, rawBucketId);
    }

    // Inherit doc from Object.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GlobalId)) {
            return false;
        }
        GlobalId rhs = (GlobalId) obj;
        return Arrays.equals(raw, rhs.raw);
    }

    public int compareTo(Object o) {
        GlobalId other = (GlobalId) o;

        for (int i=0 ; i<LENGTH; i++) {
            int thisByte = 0xF & (int) raw[i];
            int otherByte = 0xF & (int) other.raw[i];

            if (thisByte < otherByte) {
                return -1;
            } else if (thisByte > otherByte) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder strb = new StringBuilder(50);
        for (byte b : raw) {
            strb.append(" ").append(0xFF & (int) b);
        }
        return strb.toString().trim();
    }

}
