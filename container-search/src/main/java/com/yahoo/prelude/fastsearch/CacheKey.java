// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import java.util.Arrays;

import com.yahoo.collections.BobHash;
import com.yahoo.fs4.QueryPacket;


/**
 * The key used in the packet cache.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CacheKey {
    private int hashCode;
    private byte[] serialized = null;

    /**
     * Create a cache key from the query packet.
     */
    public CacheKey(QueryPacket queryPacket) {
        if (!queryPacket.isEncoded()) {
            queryPacket.allocateAndEncode(0);
        }
        this.serialized = queryPacket.getOpaqueCacheKey();
        hashCode = calculateHashCode();
    }

    private int calculateHashCode() {
        return BobHash.hash(serialized, 0);
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof CacheKey)) {
            return false;
        }

        CacheKey k = (CacheKey) o;
        return Arrays.equals(serialized, k.serialized);
        // // The following is used for detailed debugging
        // boolean state = true;
        // if (serialized.length != k.serialized.length) {
        //     System.out.println("this " + serialized.length +  " other " +  k.serialized.length);
        //     return false;
        // }
        // System.out.println("start of arrays");
        // for (int i = 0; i < serialized.length; ++i) {
        //     System.out.print("serialized " + serialized[i] + " " + k.serialized[i]);
        //     if (serialized[i] != k.serialized[i]) {
        //         System.out.println(" diff at index " + i);
        //         state = false; // want to see all the data
        //     } else {
        //         System.out.println("");
        //     }
        // }
        // return state;
    }

    public int hashCode() {
        return hashCode;
    }

    public byte[] getCopyOfFullKey() {
        return Arrays.copyOf(serialized, serialized.length);
    }

    /**
     * Return an estimate of the memory used by this object. Ie the sum of
     * the internal data fields.
     */
    public int byteSize() {
        // 4 = sizeOf(hashCode)
        return serialized.length + 4;
    }

}
