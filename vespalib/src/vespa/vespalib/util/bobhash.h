// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>

namespace vespalib {

/*
 * Hash function based on
 * http://burtleburtle.net/bob/hash/index.html
 * by Bob Jenkins, 1996. bob_jenkins@burtleburtle.net. You may use this
 * code any way you wish, private, educational, or commercial. It's free.
 *
 * Author: Michael Susag
 */



/*
  --------------------------------------------------------------------
  mix(a,b,c) -- mix 3 32-bit values reversibly.

  For every delta with one or two bits set, and the deltas of all three
  high bits or all three low bits, whether the original value of a,b,c
  is almost all zero or is uniformly distributed,
  * If mix() is run forward or backward, at least 32 bits in a,b,c
  have at least 1/4 probability of changing.
  * If mix() is run forward, every bit of c will change between 1/3 and
  2/3 of the time.  (Well, 22/100 and 78/100 for some 2-bit deltas.)
  mix() was built out of 36 single-cycle latency instructions in a
  structure that could supported 2x parallelism, like so:
  a -= b;
  a -= c; x = (c>>13);
  b -= c; a ^= x;
  b -= a; x = (a<<8);
  c -= a; b ^= x;
  c -= b; x = (b>>13);
  ...
  Unfortunately, superscalar Pentiums and Sparcs can't take advantage
  of that parallelism.  They've also turned some of those single-cycle
  latency instructions into multi-cycle latency instructions.  Still,
  this is the fastest good hash I could find.  There were about 2^^68
  to choose from.  I only looked at a billion or so.
  --------------------------------------------------------------------
*/
#define bobhash_mix(a,b,c) \
{ \
  a -= b; a -= c; a ^= (c>>13); \
  b -= c; b -= a; b ^= (a<<8); \
  c -= a; c -= b; c ^= (b>>13); \
  a -= b; a -= c; a ^= (c>>12);  \
  b -= c; b -= a; b ^= (a<<16); \
  c -= a; c -= b; c ^= (b>>5); \
  a -= b; a -= c; a ^= (c>>3);  \
  b -= c; b -= a; b ^= (a<<10); \
  c -= a; c -= b; c ^= (b>>15); \
}


class BobHash {
public:

    /**
     * @brief The hash function - hash a variable-length key into a 32-bit value
     *
     * hash() -- hash a variable-length key into a 32-bit value
     * k       : the key (unaligned variable-length array of bytes)
     * length  : the length of the key, counting by bytes
     * initval : can be any 4-byte value
     * Returns a 32-bit value.  Every bit of the key affects every bit of
     * the return value.  Every 1-bit and 2-bit delta achieves avalanche.
     * About 6*len+35 instructions.
     *
     * The best hash table sizes are powers of 2.  There is no need to do
     * mod a prime (mod is sooo slow!).  If you need less than 32 bits,
     * use a bitmask.  For example, if you need only 10 bits, do
     * h = (h & hashmask(10));
     * In which case, the hash table should have hashsize(10) elements.
     *
     * If you are hashing n strings (ub1 **)k, do it like this:
     *  for (i=0, h=0; i<n; ++i) h = hash( k[i], len[i], h);
     *
     * By Bob Jenkins, 1996.  bob_jenkins@burtleburtle.net.  You may use this
     * code any way you wish, private, educational, or commercial.  It's free.
     *
     * See http://burtleburtle.net/bob/hash/evahash.html
     * Use for hash table lookup, or anything where one collision in 2^^32 is
     * acceptable.  Do NOT use for cryptographic purposes.
     *
     * @param orig_k the key
     * @param length the length of the key
     * @param initval the previous hash, or an arbitrary value
     * @return A 32 bit hash value
     */

    static uint32_t hash(const char *orig_k,
                         uint32_t length,
                         uint32_t initval) {
        uint32_t a,b,c,len;
        const unsigned char *k;
        k = reinterpret_cast<const unsigned char *>(orig_k);

        /* Set up the internal state */
        len = length;
        a = b = 0x9e3779b9;  /* the golden ratio; an arbitrary value */
        c = initval;         /* the previous hash value */

        /*---------------------------------------- handle most of the key */
        while (len >= 12)
        {
            a += (k[0] +
                  (static_cast<uint32_t>(k[1]) << 8) +
                  (static_cast<uint32_t>(k[2]) << 16) +
                  (static_cast<uint32_t>(k[3]) << 24));
            b += (k[4] +
                  (static_cast<uint32_t>(k[5]) << 8) +
                  (static_cast<uint32_t>(k[6]) << 16) +
                  (static_cast<uint32_t>(k[7]) << 24));
            c += (k[8] +
                  (static_cast<uint32_t>(k[9]) << 8) +
                  (static_cast<uint32_t>(k[10]) << 16) +
                  (static_cast<uint32_t>(k[11]) << 24));
            bobhash_mix(a,b,c);
            k += 12; len -= 12;
        }

        /*------------------------------------- handle the last 11 bytes */
        c += length;
        switch(len)              /* all the case statements fall through */
        {
        case 11: c += (static_cast<uint32_t>(k[10]) << 24); [[fallthrough]];
        case 10: c += (static_cast<uint32_t>(k[9]) << 16); [[fallthrough]];
        case 9 : c += (static_cast<uint32_t>(k[8]) << 8); [[fallthrough]];
            /* the first byte of c is reserved for the length */
        case 8 : b += (static_cast<uint32_t>(k[7]) << 24); [[fallthrough]];
        case 7 : b += (static_cast<uint32_t>(k[6]) << 16); [[fallthrough]];
        case 6 : b += (static_cast<uint32_t>(k[5]) << 8); [[fallthrough]];
        case 5 : b += k[4]; [[fallthrough]];
        case 4 : a += (static_cast<uint32_t>(k[3]) << 24); [[fallthrough]];
        case 3 : a += (static_cast<uint32_t>(k[2]) << 16); [[fallthrough]];
        case 2 : a += (static_cast<uint32_t>(k[1]) << 8); [[fallthrough]];
        case 1 : a += k[0];
            /* case 0: nothing left to add */
        }
        bobhash_mix(a,b,c);
        /*-------------------------------------------- report the result */
        return c;
    }
};

}

