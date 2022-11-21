package com.yahoo.compress;

import net.openhft.hashing.LongHashFunction;

public class Hasher {
    public static long xxh3(byte [] data) {
        return LongHashFunction.xx3().hashBytes(data);
    }
}
