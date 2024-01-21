// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import net.openhft.hashing.LongHashFunction;

/**
 * Utility for hashing providing multiple hashing methods
 * @author baldersheim
 */
public class Hasher {
    private final LongHashFunction hasher;
    /** Uses net.openhft.hashing.LongHashFunction.xx3() */
    public static long xxh3(byte [] data) {
        return LongHashFunction.xx3().hashBytes(data);
    }
    public static long xxh3(byte [] data, long seed) {
        return LongHashFunction.xx3(seed).hashBytes(data);
    }

    private Hasher(LongHashFunction hasher) {
        this.hasher = hasher;
    }
    public static Hasher withSeed(long seed) {
        return new Hasher(LongHashFunction.xx3(seed));
    }
    public long hash(long v) {
        return hasher.hashLong(v);
    }
    public long hash(String s) {
        return hasher.hashChars(s);
    }
}
