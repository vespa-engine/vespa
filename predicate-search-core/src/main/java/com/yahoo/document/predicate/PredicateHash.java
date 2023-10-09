// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import com.yahoo.text.Utf8;

/**
 * This hash function must match the corresponding C++ hash function in 'ConvertClass',
 * currently located at 'rise_platform/src/interface/cpp/api/ConvertClass.h'
 *
 * @author Magnar Nedland
 */
public class PredicateHash {
    @SuppressWarnings("fallthrough")
    public static long hash64(String s) {
        byte[] bytes = Utf8.toBytes(s);
        long a=0L, b=0L;
        long c=0x9e3779b97f4a7c13L;
        int len = bytes.length;
        int offset = 0;
        while (len >= 24) {
            c += ((0xffL & (long)bytes[offset + 23]) << 56) +
                    ((0xffL & (long)bytes[offset + 22]) << 48) +
                    ((0xffL & (long)bytes[offset + 21]) << 40) +
                    ((0xffL & (long)bytes[offset + 20]) << 32) +
                    ((0xffL & (long)bytes[offset + 19]) << 24) +
                    ((0xffL & (long)bytes[offset + 18]) << 16) +
                    ((0xffL & (long)bytes[offset + 17]) << 8) +
                    ((0xffL & (long)bytes[offset + 16]));

            b += ((0xffL & (long)bytes[offset + 15]) << 56) +
                    ((0xffL & (long)bytes[offset + 14]) << 48) +
                    ((0xffL & (long)bytes[offset + 13]) << 40) +
                    ((0xffL & (long)bytes[offset + 12]) << 32) +
                    ((0xffL & (long)bytes[offset + 11]) << 24) +
                    ((0xffL & (long)bytes[offset + 10]) << 16) +
                    ((0xffL & (long)bytes[offset + 9]) << 8) +
                    ((0xffL & (long)bytes[offset + 8]));

            a += ((0xffL & (long)bytes[offset + 7]) << 56) +
                    ((0xffL & (long)bytes[offset + 6]) << 48) +
                    ((0xffL & (long)bytes[offset + 5]) << 40) +
                    ((0xffL & (long)bytes[offset + 4]) << 32) +
                    ((0xffL & (long)bytes[offset + 3]) << 24) +
                    ((0xffL & (long)bytes[offset + 2]) << 16) +
                    ((0xffL & (long)bytes[offset + 1]) << 8) +
                    ((0xffL & (long)bytes[offset + 0]));

            // Mix.  This arithmetic must match the mix below.
            a -= b; a -= c; a ^= (c>>>43);
            b -= c; b -= a; b ^= (a<<9);
            c -= a; c -= b; c ^= (b>>>8);
            a -= b; a -= c; a ^= (c>>>38);
            b -= c; b -= a; b ^= (a<<23);
            c -= a; c -= b; c ^= (b>>>5);
            a -= b; a -= c; a ^= (c>>>35);
            b -= c; b -= a; b ^= (a<<49);
            c -= a; c -= b; c ^= (b>>>11);
            a -= b; a -= c; a ^= (c>>>12);
            b -= c; b -= a; b ^= (a<<18);
            c -= a; c -= b; c ^= (b>>>22);
            // End mix.

            offset += 24;
            len -= 24;
        }

        c += bytes.length;
        switch (len) {
            case 23: c+= (0xffL & (long)bytes[offset + 22]) << 56;
            case 22: c+= (0xffL & (long)bytes[offset + 21]) << 48;
            case 21: c+= (0xffL & (long)bytes[offset + 20]) << 40;
            case 20: c+= (0xffL & (long)bytes[offset + 19]) << 32;
            case 19: c+= (0xffL & (long)bytes[offset + 18]) << 24;
            case 18: c+= (0xffL & (long)bytes[offset + 17]) << 16;
            case 17: c+= (0xffL & (long)bytes[offset + 16]) << 8;
                // The first byte of c is reserved for the length
            case 16: b+= (0xffL & (long)bytes[offset + 15]) << 56;
            case 15: b+= (0xffL & (long)bytes[offset + 14]) << 48;
            case 14: b+= (0xffL & (long)bytes[offset + 13]) << 40;
            case 13: b+= (0xffL & (long)bytes[offset + 12]) << 32;
            case 12: b+= (0xffL & (long)bytes[offset + 11]) << 24;
            case 11: b+= (0xffL & (long)bytes[offset + 10]) << 16;
            case 10: b+= (0xffL & (long)bytes[offset + 9]) << 8;
            case 9:  b+= (0xffL & (long)bytes[offset + 8]);
            case 8:  a+= (0xffL & (long)bytes[offset + 7]) << 56;
            case 7:  a+= (0xffL & (long)bytes[offset + 6]) << 48;
            case 6:  a+= (0xffL & (long)bytes[offset + 5]) << 40;
            case 5:  a+= (0xffL & (long)bytes[offset + 4]) << 32;
            case 4:  a+= (0xffL & (long)bytes[offset + 3]) << 24;
            case 3:  a+= (0xffL & (long)bytes[offset + 2]) << 16;
            case 2:  a+= (0xffL & (long)bytes[offset + 1]) << 8;
            case 1:  a+= (0xffL & (long)bytes[offset + 0]);
        }
        // Mix.  This arithmetic must match the mix above.
        a -= b; a -= c; a ^= (c>>>43);
        b -= c; b -= a; b ^= (a<<9);
        c -= a; c -= b; c ^= (b>>>8);
        a -= b; a -= c; a ^= (c>>>38);
        b -= c; b -= a; b ^= (a<<23);
        c -= a; c -= b; c ^= (b>>>5);
        a -= b; a -= c; a ^= (c>>>35);
        b -= c; b -= a; b ^= (a<<49);
        c -= a; c -= b; c ^= (b>>>11);
        a -= b; a -= c; a ^= (c>>>12);
        b -= c; b -= a; b ^= (a<<18);
        c -= a; c -= b; c ^= (b>>>22);
        // End mix.

        return c;
    }

}
