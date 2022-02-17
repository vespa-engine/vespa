// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import com.yahoo.text.Utf8;

/**
 * A Java port of Michael Susag's BobHash in FastLib. This version is
 * specifically done to be bit compatible with the one in FastLib, as it
 * is used in decoding packets from FastServer.
 *
 * Hash function based on
 * <a href="http://burtleburtle.net/bob/hash/index.html">http://burtleburtle.net/bob/hash/index.html</a>
 * by Bob Jenkins, 1996. bob_jenkins@burtleburtle.net. You may use this
 * code any way you wish, private, educational, or commercial. It's free.
 *
 * @author Michael Susag
 * @author Steinar Knutsen
 */
public class BobHash {

    /**
     * mix -- mix 3 32-bit values reversibly.
     * For every delta with one or two bits set, and the deltas of all three
     * high bits or all three low bits, whether the original value of a,b,c
     * is almost all zero or is uniformly distributed,
     * If mix() is run forward or backward, at least 32 bits in a,b,c
     * have at least 1/4 probability of changing.
     * If mix() is run forward, every bit of c will change between 1/3 and
     * 2/3 of the time.  (Well, 22/100 and 78/100 for some 2-bit deltas.)
     * mix() was built out of 36 single-cycle latency instructions in a
     * structure that could supported 2x parallelism, like so:
     *
     * <pre>
     *       a -= b;
     *       a -= c; x = (c&gt;&gt;13);
     *       b -= c; a ^= x;
     *       b -= a; x = (a&lt;&lt;8);
     *       c -= a; b ^= x;
     *       c -= b; x = (b&gt;&gt;13);
     *       ...
     * </pre>
     *
     * <p>
     * Unfortunately, superscalar Pentiums and Sparcs can't take advantage
     * of that parallelism.  They've also turned some of those single-cycle
     * latency instructions into multi-cycle latency instructions.  Still,
     * this is the fastest good hash I could find.  There were about 2^^68
     * to choose from.  I only looked at a billion or so.
     */
    private static int[] mix(int a, int b, int c) {
        a -= b; a -= c; a ^= (c >>> 13);
        b -= c; b -= a; b ^= (a << 8);
        c -= a; c -= b; c ^= (b >>> 13);
        a -= b; a -= c; a ^= (c >>> 12);
        b -= c; b -= a; b ^= (a << 16);
        c -= a; c -= b; c ^= (b >>> 5);
        a -= b; a -= c; a ^= (c >>> 3);
        b -= c; b -= a; b ^= (a << 10);
        c -= a; c -= b; c ^= (b >>> 15);

        return new int[]{ a, b, c };
    }

    /**
     * Transform a byte to an int viewed as an unsigned byte.
     */
    private static int unsign(byte x) {
        int y;

        y = 0xFF & x;
        return y;
    }

    /**
     * Hashes a string, by calling hash(byte[] key,int initval) with
     * the utf-8 bytes of the string as key and 0 as initval.
     * Note: This is copying the string content, change implementation to
     * use efficiently on large strings.
     *
     * bratseth
     */
    public static int hash(String key) {
        return hash(Utf8.toBytes(key), 0);
    }

    /**
     * The hash function
     *
     * <p>
     * hash() -- hash a variable-length key into a 32-bit value<br>
     * k       : the key (the unaligned variable-length array of bytes)<br>
     * len     : the length of the key, counting by bytes<br>
     * initval : can be any 4-byte value
     *
     * <p>
     * Returns a 32-bit value.  Every bit of the key affects every bit of
     * the return value.  Every 1-bit and 2-bit delta achieves avalanche.
     * About 6*len+35 instructions.
     *
     * <p>
     * The best hash table sizes are powers of 2.  There is no need to do
     * mod a prime (mod is sooo slow!).  If you need less than 32 bits,
     * use a bitmask.  For example, if you need only 10 bits, do
     * h = (h &amp; hashmask(10));
     * In which case, the hash table should have hashsize(10) elements.
     *
     * If you are hashing n strings (ub1 **)k, do it like this:
     *  for (i=0, h=0; i&lt;n; ++i) h = hash( k[i], len[i], h);
     *
     * <p>
     * By Bob Jenkins, 1996.  bob_jenkins@burtleburtle.net.  You may use this
     * code any way you wish, private, educational, or commercial.  It's free.
     *
     * <p>
     * See http://burtleburtle.net/bob/hash/evahash.html
     * Use for hash table lookup, or anything where one collision in 2^^32 is
     * acceptable.  Do NOT use for cryptographic purposes.
     *
     * @param k the key
     * @param initval the previous hash, or an arbitrary value
     * @return A 32 bit hash value
     */
    @SuppressWarnings("fallthrough")
    public static int hash(byte[] k, int initval) {
        int a, b, c, len;
        int offset = 0;
        int[] abcBuffer;

        /* Set up the internal state */
        len = k.length;
        a = b = 0x9e3779b9; /* the golden ratio; an arbitrary value */
        c = initval; /* the previous hash value */

        // handle most of the key
        while (len >= 12) {
            a += (unsign(k[offset + 0]) + (unsign(k[offset + 1]) << 8)
                    + (unsign(k[offset + 2]) << 16)
                    + (unsign(k[offset + 3]) << 24));
            b += (unsign(k[offset + 4]) + (unsign(k[offset + 5]) << 8)
                    + (unsign(k[offset + 6]) << 16)
                    + (unsign(k[offset + 7]) << 24));
            c += (unsign(k[offset + 8]) + (unsign(k[offset + 9]) << 8)
                    + (unsign(k[offset + 10]) << 16)
                    + (unsign(k[offset + 11]) << 24));
            abcBuffer = mix(a, b, c);
            a = abcBuffer[0];
            b = abcBuffer[1];
            c = abcBuffer[2];
            offset += 12;
            len -= 12;
        }

        // handle the last 11 bytes
        c += k.length;
        switch (len) {
            // all the case statements fall through
            case 11:
                c += (unsign(k[offset + 10]) << 24);

            case 10:
                c += (unsign(k[offset + 9]) << 16);

            case 9:
                c += (unsign(k[offset + 8]) << 8);

                /* the first byte of c is reserved for the length */
            case 8:
                b += (unsign(k[offset + 7]) << 24);

            case 7:
                b += (unsign(k[offset + 6]) << 16);

            case 6:
                b += (unsign(k[offset + 5]) << 8);

            case 5:
                b += unsign(k[offset + 4]);

            case 4:
                a += (unsign(k[offset + 3]) << 24);

            case 3:
                a += (unsign(k[offset + 2]) << 16);

            case 2:
                a += (unsign(k[offset + 1]) << 8);

            case 1:
                a += unsign(k[offset + 0]);

                /* case 0: nothing left to add */
        }
        abcBuffer = mix(a, b, c);
        return abcBuffer[2];
    }

}
