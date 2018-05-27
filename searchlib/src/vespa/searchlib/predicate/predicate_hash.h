// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace predicate {
/**
 * Hash function used for predicate fields.
 */
struct PredicateHash {
    static uint64_t hash64(vespalib::stringref aKey) {
        return hash64(aKey.data(), aKey.size());
    }

    static uint64_t hash64(const void *data, uint32_t origLen) {
        int64_t a, b, c;
        int offset;  // Current offset into the entire key.

        const uint8_t *aKey = static_cast<const uint8_t *>(data);
    
        // Set up the internal state
        int anInitval = 0;
        a = b = anInitval;  // the previous hash value
        c = 0x9e3779b97f4a7c13LL;  // the golden ratio; an arbitrary value
        offset = 0;
        uint32_t len = origLen;

        // handle most of the key
        while (len >= 24) {
            a += ((0xffLL & aKey[offset+0]) +
                  ((0xffLL & aKey[offset+1])<<8) +
                  ((0xffLL & aKey[offset+2])<<16) +
                  ((0xffLL & aKey[offset+3])<<24) +
                  ((0xffLL & aKey[offset+4])<<32) +
                  ((0xffLL & aKey[offset+5])<<40) +
                  ((0xffLL & aKey[offset+6])<<48) +
                  ((0xffLL & aKey[offset+7])<<56));
            b += ((0xffLL & aKey[offset+8]) + 
                  ((0xffLL & aKey[offset+9])<<8) +
                  ((0xffLL & aKey[offset+10])<<16) +
                  ((0xffLL & aKey[offset+11])<<24) +
                  ((0xffLL & aKey[offset+12])<<32) +
                  ((0xffLL & aKey[offset+13])<<40) +
                  ((0xffLL & aKey[offset+14])<<48) +
                  ((0xffLL & aKey[offset+15])<<56));
            c += ((0xffLL & aKey[offset+16]) +
                  ((0xffLL & aKey[offset+17])<<8) +
                  ((0xffLL & aKey[offset+18])<<16) +
                  ((0xffLL & aKey[offset+19])<<24) +
                  ((0xffLL & aKey[offset+20])<<32) +
                  ((0xffLL & aKey[offset+21])<<40) +
                  ((0xffLL & aKey[offset+22])<<48) +
                  ((0xffLL & aKey[offset+23])<<56));
    
            // Mix.  This arithmetic must match the mix below.
            a -= b; a -= c; a ^= (((uint64_t) c)>>43);
            b -= c; b -= a; b ^= (a<<9);
            c -= a; c -= b; c ^= (((uint64_t) b)>>8);
            a -= b; a -= c; a ^= (((uint64_t) c)>>38);
            b -= c; b -= a; b ^= (a<<23);
            c -= a; c -= b; c ^= (((uint64_t) b)>>5);
            a -= b; a -= c; a ^= (((uint64_t) c)>>35);
            b -= c; b -= a; b ^= (a<<49);
            c -= a; c -= b; c ^= (((uint64_t) b)>>11);
            a -= b; a -= c; a ^= (((uint64_t) c)>>12);
            b -= c; b -= a; b ^= (a<<18);
            c -= a; c -= b; c ^= (((uint64_t) b)>>22);
            // End mix.

            offset += 24; len -= 24;
        }

        // handle the last 23 bytes
        c += origLen;  
        switch(len) {  // all the case statements fall through
        case 23: c+=((0xffLL & aKey[offset+22])<<56); [[fallthrough]];
        case 22: c+=((0xffLL & aKey[offset+21])<<48); [[fallthrough]];
        case 21: c+=((0xffLL & aKey[offset+20])<<40); [[fallthrough]];
        case 20: c+=((0xffLL & aKey[offset+19])<<32); [[fallthrough]];
        case 19: c+=((0xffLL & aKey[offset+18])<<24); [[fallthrough]];
        case 18: c+=((0xffLL & aKey[offset+17])<<16); [[fallthrough]];
        case 17: c+=((0xffLL & aKey[offset+16])<<8); [[fallthrough]];
            // the first byte of c is reserved for the length
        case 16: b+=((0xffLL & aKey[offset+15])<<56); [[fallthrough]];
        case 15: b+=((0xffLL & aKey[offset+14])<<48); [[fallthrough]];
        case 14: b+=((0xffLL & aKey[offset+13])<<40); [[fallthrough]];
        case 13: b+=((0xffLL & aKey[offset+12])<<32); [[fallthrough]];
        case 12: b+=((0xffLL & aKey[offset+11])<<24); [[fallthrough]];
        case 11: b+=((0xffLL & aKey[offset+10])<<16); [[fallthrough]];
        case 10: b+=((0xffLL & aKey[offset+ 9])<<8); [[fallthrough]];
        case  9: b+=( 0xffLL & aKey[offset+ 8]); [[fallthrough]];
        case  8: a+=((0xffLL & aKey[offset+ 7])<<56); [[fallthrough]];
        case  7: a+=((0xffLL & aKey[offset+ 6])<<48); [[fallthrough]];
        case  6: a+=((0xffLL & aKey[offset+ 5])<<40); [[fallthrough]];
        case  5: a+=((0xffLL & aKey[offset+ 4])<<32); [[fallthrough]];
        case  4: a+=((0xffLL & aKey[offset+ 3])<<24); [[fallthrough]];
        case  3: a+=((0xffLL & aKey[offset+ 2])<<16); [[fallthrough]];
        case  2: a+=((0xffLL & aKey[offset+ 1])<<8); [[fallthrough]];
        case  1: a+=( 0xffLL & aKey[offset+ 0]);
            // case 0: nothing left to add
        }

        // Mix.  This arithmetic must match the mix above.
        a -= b; a -= c; a ^= (((uint64_t) c)>>43);
        b -= c; b -= a; b ^= (a<<9);
        c -= a; c -= b; c ^= (((uint64_t) b)>>8);
        a -= b; a -= c; a ^= (((uint64_t) c)>>38);
        b -= c; b -= a; b ^= (a<<23);
        c -= a; c -= b; c ^= (((uint64_t) b)>>5);
        a -= b; a -= c; a ^= (((uint64_t) c)>>35);
        b -= c; b -= a; b ^= (a<<49);
        c -= a; c -= b; c ^= (((uint64_t) b)>>11);
        a -= b; a -= c; a ^= (((uint64_t) c)>>12);
        b -= c; b -= a; b ^= (a<<18);
        c -= a; c -= b; c ^= (((uint64_t) b)>>22);
        // End mix.

        return static_cast<uint64_t>(c);
    }
};
}  // namespace predicate
}  // namespace search

