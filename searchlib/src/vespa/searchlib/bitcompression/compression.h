// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/comprfile.h>
#include <vespa/vespalib/stllike/string.h>
#include <cassert>

namespace vespalib {

class GenericHeader;
template <typename T> class ConstArrayRef;

}

namespace search::index {
    class DocIdAndFeatures;
    class PostingListParams;
}

namespace search::fef { class TermFieldMatchDataArray; }

namespace search::bitcompression {

class Position {
public:
    Position(const uint64_t * occurences, int bitOffset) : _occurences(occurences), _bitOffset(bitOffset) { }
    const uint64_t * getOccurences() const { return _occurences; }
    int getBitOffset() const { return _bitOffset; }
private:
    const uint64_t * _occurences;
    int              _bitOffset;
};

/*
 * The so-called rice2 code is very similar to the well known exp
 * golomb code.  One difference is that the first bits are inverted.
 * rice code is a special case of golomb code, with M being a power of
 * two (2^k).  rice coding uses unary coding for quotient, while remainder
 * bits are just written as they are.
 *
 * Rice2 (k=0) starts with:      0, 100, 101, 11000, 11001, 11010, 11011
 * Rice2 (k=1) starts with:      00, 01, 1000, 1001, 1010, 1011, 110000
 * Exp golomb (k=0) starts with: 1, 010, 011, 00100, 00101, 00101, 00111
 * Exp golomb (k=1) starts with: 10, 11, 0100, 0101, 0110, 0111, 001000
 * unary coding:                 0, 10, 110, 1110, 11110, 111110, 1111110
 * rice coding (k=0)             0, 10, 110, 1110, 11110, 111110, 1111110
 * rice coding (k=1)             00, 01, 100, 101, 1100, 1101, 11100
 *
 * For k=0, exp golomb coding is the same as elias gamma coding.
 * For k=0, rice coding is the same as unary coding.
 *
 * k values up to and including 63 is supported for exp golomb coding
 * and decoding.

 * The *SMALL* macros only supports k values up to and including 62
 * (trading flexibility for a minor speed improvement) and numbers
 * that can be encoded within 64 bits.
 */

#define TOP_BIT64 UINT64_C(0x8000000000000000)
#define TOP_2_BITS64 UINT64_C(0xC000000000000000)
#define TOP_4_BITS64 UINT64_C(0xF000000000000000)

// Compression parameters for zcposting file word headers.
#define K_VALUE_ZCPOSTING_NUMDOCS 0
#define K_VALUE_ZCPOSTING_LASTDOCID 22
#define K_VALUE_ZCPOSTING_DOCIDSSIZE 22
#define K_VALUE_ZCPOSTING_L1SKIPSIZE 12
#define K_VALUE_ZCPOSTING_L2SKIPSIZE 10
#define K_VALUE_ZCPOSTING_L3SKIPSIZE 8
#define K_VALUE_ZCPOSTING_L4SKIPSIZE 6
#define K_VALUE_ZCPOSTING_FEATURESSIZE 25
#define K_VALUE_ZCPOSTING_DELTA_DOCID 22
#define K_VALUE_ZCPOSTING_FIELD_LENGTH 9
#define K_VALUE_ZCPOSTING_NUM_OCCS 0

/**
 * Lookup tables used for compression / decompression.
 */
class CodingTables
{
public:
    static uint64_t _intMask64[65];
    static uint64_t _intMask64le[65];
};

#define UC64_DECODECONTEXT(prefix)                  \
  const uint64_t * prefix ## Compr;                 \
  uint64_t prefix ## Val;                           \
  uint64_t prefix ## CacheInt;                      \
  uint32_t prefix ## PreRead;

#define UC64_DECODECONTEXT_CONSTRUCTOR(prefix, ctx)         \
  const uint64_t * prefix ## Compr = ctx ## valI;           \
  uint64_t prefix ## Val = ctx ## val;                      \
  uint64_t prefix ## CacheInt = ctx ## cacheInt;            \
  uint32_t prefix ## PreRead = ctx ## preRead;

#define UC64_DECODECONTEXT_LOAD(prefix, ctx)                \
  prefix ## Compr = ctx ## valI;                            \
  prefix ## Val = ctx ## val;                               \
  prefix ## CacheInt = ctx ## cacheInt;                     \
  prefix ## PreRead = ctx ## preRead;

#define UC64_DECODECONTEXT_LOAD_PARTIAL(prefix, ctx)            \
  prefix ## Compr = ctx ## valI;

#define UC64_DECODECONTEXT_STORE(prefix, ctx)               \
  ctx ## valI = prefix ## Compr;                            \
  ctx ## val = prefix ## Val;                               \
  ctx ## cacheInt = prefix ## CacheInt;                     \
  ctx ## preRead = prefix ## PreRead;


#define UC64_DECODECONTEXT_STORE_PARTIAL(prefix, ctx)           \
  ctx ## valI = prefix ## Compr;

#define UC64BE_READBITS(val, valI, preRead, cacheInt, EC)        \
  do {                                                           \
    if (__builtin_expect(length <= preRead, true)) {             \
      val |= ((cacheInt >> (preRead - length)) &                 \
    ::search::bitcompression::CodingTables::_intMask64[length]); \
      preRead -= length;                                         \
    } else {                                                     \
      if (__builtin_expect(preRead > 0, true)) {                 \
    length -= preRead;                                           \
    val |= ((cacheInt &                                          \
      ::search::bitcompression::CodingTables::                   \
      _intMask64[preRead]) << length);                           \
      }                                                          \
      cacheInt = EC::bswap(*valI++);                             \
      preRead = 64 - length;                                     \
      val |= cacheInt >> preRead;                                \
    }                                                            \
  } while (0)

#define UC64BE_READBITS_NS(prefix, EC)                      \
  UC64BE_READBITS(prefix ## Val, prefix ## Compr,           \
          prefix ## PreRead, prefix ## CacheInt, EC)

#define UC64BE_READBITS_CTX(ctx, EC)                    \
  UC64BE_READBITS(ctx._val, ctx._valI,                  \
          ctx._preRead, ctx._cacheInt, EC);


#define UC64BE_SETUPBITS(bitOffset, val, valI, preRead, cacheInt, EC) \
  do {                                                                \
    cacheInt = EC::bswap(*valI++);                                    \
    preRead = 64 - bitOffset;                                         \
    val = 0;                                                          \
    length = 64;                                                      \
    UC64BE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)

#define UC64BE_SETUPBITS_NS(ns, comprData, bitOffset, EC)       \
    ns ## Compr = comprData;                                    \
    UC64BE_SETUPBITS((bitOffset), ns ## Val, ns ## Compr,       \
             ns ## PreRead, ns ## CacheInt, EC);

#define UC64BE_SETUPBITS_CTX(ctx, comprData, bitOffset, EC)     \
    ctx._valI = comprData;                                      \
    UC64BE_SETUPBITS((bitOffset), ctx._val, ctx._valI,          \
             ctx._preRead, ctx._cacheInt, EC);

#define UC64BE_DECODEEXPGOLOMB(val, valI, preRead, cacheInt, k, EC)   \
  do {                                                                \
    length = __builtin_clzl(val);                                     \
    unsigned int olength = length;                                    \
    val <<= length;                                                   \
    if (__builtin_expect(length * 2 + 1 + (k) > 64, false)) {         \
      UC64BE_READBITS(val, valI, preRead, cacheInt, EC);              \
      length = 0;                                                     \
    }                                                                 \
    val64 = (val >> (63 - olength - (k))) - (UINT64_C(1) << (k));     \
    if (__builtin_expect(olength + 1 + (k) != 64, true)) {            \
      val <<= olength + 1 + (k);                                      \
    } else {                                                          \
      val = 0;                                                        \
    }                                                                 \
    length += olength + 1 + (k);                                      \
    UC64BE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)


#define UC64BE_DECODEEXPGOLOMB_NS(prefix, k, EC)            \
  do {                                                      \
    UC64BE_DECODEEXPGOLOMB(prefix ## Val, prefix ## Compr,  \
               prefix ## PreRead, prefix ## CacheInt,       \
               k, EC);                                      \
  } while (0)

#define UC64BE_DECODEEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, \
                     EC)                                              \
  do {                                                                \
    length = __builtin_clzl(val);                                   \
    val <<= length;                                                   \
    val64 = (val >> (63 - length - (k))) - (UINT64_C(1) << (k));      \
    val <<= length + 1 + (k);                                         \
    length += length + 1 + (k);                                       \
    UC64BE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)

#define UC64BE_DECODEEXPGOLOMB_SMALL_NS(prefix, k, EC)           \
  do {                                                           \
    UC64BE_DECODEEXPGOLOMB_SMALL(prefix ## Val, prefix ## Compr, \
                 prefix ## PreRead, prefix ## CacheInt,          \
                 k, EC);                                         \
  } while (0)

#define UC64BE_DECODEEXPGOLOMB_SMALL_CTX(ctx, k, EC)            \
  do {                                                          \
    UC64BE_DECODEEXPGOLOMB_SMALL(ctx._val, ctx._valI,           \
                 ctx._preRead, ctx._cacheInt,                   \
                 k, EC);                                        \
  } while (0)

#define UC64BE_DECODEEXPGOLOMB_SMALL_APPLY(val, valI, preRead, cacheInt, \
                       k, EC, resop)                                     \
  do {                                                                   \
    length = __builtin_clzl(val);                                        \
    val <<= length;                                                      \
    resop (val >> (63 - length - (k))) - (UINT64_C(1) << (k));           \
    val <<= length + 1 + (k);                                            \
    length += length + 1 + (k);                                          \
    UC64BE_READBITS(val, valI, preRead, cacheInt, EC);                   \
  } while (0)


#define UC64BE_SKIPEXPGOLOMB(val, valI, preRead, cacheInt, k, EC)     \
  do {                                                                \
    length = __builtin_clzl(val);                                     \
    unsigned int olength = length;                                    \
    val <<= length;                                                   \
    if (__builtin_expect(length * 2 + 1 + (k) > 64, false)) {         \
      UC64BE_READBITS(val, valI, preRead, cacheInt, EC);              \
      length = 0;                                                     \
    }                                                                 \
    if (__builtin_expect(olength + 1 + (k) != 64, true)) {            \
      val <<= olength + 1 + (k);                                      \
    } else {                                                          \
      val = 0;                                                        \
    }                                                                 \
    length += olength + 1 + (k);                                      \
    UC64BE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)


#define UC64BE_SKIPEXPGOLOMB_NS(prefix, k, EC)           \
  do {                                                   \
    UC64BE_SKIPEXPGOLOMB(prefix ## Val, prefix ## Compr, \
             prefix ## PreRead, prefix ## CacheInt,      \
             k, EC);                                     \
  } while (0)

#define UC64BE_SKIPEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k,   \
                   EC)                                                \
  do {                                                                \
    length = __builtin_clzl(val);                                     \
    val <<= length;                                                   \
    val <<= length + 1 + (k);                                         \
    length += length + 1 + (k);                                       \
    UC64BE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)

#define UC64BE_SKIPEXPGOLOMB_SMALL_NS(prefix, k, EC)           \
  do {                                                         \
    UC64BE_SKIPEXPGOLOMB_SMALL(prefix ## Val, prefix ## Compr, \
                   prefix ## PreRead, prefix ## CacheInt,      \
                   k, EC);                                     \
  } while (0)

#define UC64BE_WRITEBITS(cacheInt, cacheFree, bufI, EC)       \
  do {                                                        \
    if (length >= cacheFree) {                                \
        cacheInt |= ((data >> (length - cacheFree)) &         \
                     ::search::bitcompression::CodingTables:: \
             _intMask64[cacheFree]);                          \
        *bufI++ = EC::bswap(cacheInt);                        \
        length -= cacheFree;                                  \
        cacheInt = 0;                                         \
        cacheFree = 64;                                       \
    }                                                         \
    if (length > 0) {                                         \
        uint64_t dataFragment =                               \
            (data & ::search::bitcompression::CodingTables::  \
            _intMask64[length]);                              \
        cacheInt |= (dataFragment << (cacheFree - length));   \
        cacheFree -= length;                                  \
    }                                                         \
  } while (0)


#define UC64BE_WRITEBITS_NS(prefix, EC)                       \
  do {                                                        \
    UC64BE_WRITEBITS(prefix ## CacheInt, prefix ## CacheFree, \
                          prefix ## BufI, EC);                \
  } while (0)

#define UC64BE_WRITEBITS_CTX(ctx, EC)                   \
  do {                                                  \
    UC64BE_WRITEBITS(ctx ## cacheInt, ctx ## cacheFree, \
                   ctx ## valI, EC);                    \
  } while (0)

#define UC64BE_DECODEDEXPGOLOMB_NS(prefix, k, EC)           \
  do {                                                      \
    if ((prefix ## Val & TOP_BIT64) == 0) {                 \
      length = 1;                                           \
      prefix ## Val <<= 1;                                  \
      val64 = 0;                                            \
      UC64BE_READBITS_NS(prefix, EC);                       \
    } else {                                                \
      if ((prefix ## Val & TOP_2_BITS64) != TOP_2_BITS64) { \
    length = 2;                                             \
    prefix ## Val <<= 2;                                    \
    val64 = 1;                                              \
    UC64BE_READBITS_NS(prefix, EC);                         \
      } else {                                              \
    length = 2;                                             \
    prefix ## Val <<= 2;                                    \
    UC64BE_READBITS_NS(prefix, EC);                         \
    UC64BE_DECODEEXPGOLOMB_NS(prefix, k, EC);               \
    val64 += 2;                                             \
      }                                                     \
    }                                                       \
  } while (0)

#define UC64BE_DECODED0EXPGOLOMB_NS(prefix, k, EC) \
  do {                                             \
    if ((prefix ## Val & TOP_BIT64) == 0) {        \
      length = 1;                                  \
      prefix ## Val <<= 1;                         \
      val64 = 0;                                   \
      UC64BE_READBITS_NS(prefix, EC);              \
    } else {                                       \
      length = 1;                                  \
      prefix ## Val <<= 1;                         \
      UC64BE_READBITS_NS(prefix, EC);              \
      UC64BE_DECODEEXPGOLOMB_NS(prefix, k, EC);    \
      val64 += 1;                                  \
    }                                              \
  } while (0)

#define UC64LE_READBITS(val, valI, preRead, cacheInt, EC)          \
  do {                                                             \
    if (__builtin_expect(length <= preRead, true)) {               \
      val |= ((cacheInt << (preRead - length)) &                   \
    ::search::bitcompression::CodingTables::_intMask64le[length]); \
      preRead -= length;                                           \
    } else {                                                       \
      if (__builtin_expect(preRead > 0, true)) {                   \
    length -= preRead;                                             \
    val |= ((cacheInt &                                            \
      ::search::bitcompression::CodingTables::                     \
      _intMask64le[preRead]) >> length);                           \
      }                                                            \
      cacheInt = EC::bswap(*valI++);                               \
      preRead = 64 - length;                                       \
      val |= cacheInt << preRead;                                  \
    }                                                              \
  } while (0)

#define UC64LE_READBITS_NS(prefix, EC)               \
  UC64LE_READBITS(prefix ## Val, prefix ## Compr,    \
          prefix ## PreRead, prefix ## CacheInt, EC)

#define UC64LE_READBITS_CTX(ctx, EC)                 \
  UC64LE_READBITS(ctx._val, ctx._valI,               \
          ctx._preRead, ctx._cacheInt, EC);


#define UC64LE_SETUPBITS(bitOffset, val, valI, preRead, cacheInt, EC) \
  do {                                                                \
    cacheInt = EC::bswap(*valI++);                                    \
    preRead = 64 - bitOffset;                                         \
    val = 0;                                                          \
    length = 64;                                                      \
    UC64LE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)

#define UC64LE_SETUPBITS_NS(ns, comprData, bitOffset, EC)       \
    ns ## Compr = comprData;                                    \
    UC64LE_SETUPBITS((bitOffset), ns ## Val, ns ## Compr,       \
             ns ## PreRead, ns ## CacheInt, EC);

#define UC64LE_SETUPBITS_CTX(ctx, comprData, bitOffset, EC)     \
    ctx._valI = comprData;                                      \
    UC64LE_SETUPBITS((bitOffset), ctx._val, ctx._valI,          \
             ctx._preRead, ctx._cacheInt, EC);

#define UC64LE_DECODEEXPGOLOMB(val, valI, preRead, cacheInt, k, EC) \
  do {                                                              \
    unsigned int olength = __builtin_ctzl(val);                     \
    length = olength + 1;                                           \
    if (__builtin_expect(length != 64, true)) {                     \
      val >>= length;                                               \
    } else {                                                        \
      val = 0;                                                      \
    }                                                               \
    if (__builtin_expect(olength * 2 + 1 + (k) > 64, false)) {      \
      UC64LE_READBITS(val, valI, preRead, cacheInt, EC);            \
      length = 0;                                                   \
    }                                                               \
    val64 = (val & ((UINT64_C(1) << (olength + (k))) - 1)) +        \
       (UINT64_C(1) << (olength + (k))) - (UINT64_C(1) << (k));     \
    val >>= olength + (k);                                          \
    length += olength + (k);                                        \
    UC64LE_READBITS(val, valI, preRead, cacheInt, EC);              \
  } while (0)


#define UC64LE_DECODEEXPGOLOMB_NS(prefix, k, EC)            \
  do {                                                      \
    UC64LE_DECODEEXPGOLOMB(prefix ## Val, prefix ## Compr,  \
               prefix ## PreRead, prefix ## CacheInt,       \
               k, EC);                                      \
  } while (0)

#define UC64LE_DECODEEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, \
                     EC)                                              \
  do {                                                                \
    length = __builtin_ctzl(val);                                     \
    val >>= length + 1;                                               \
    val64 = (val & ((UINT64_C(1) << (length + (k))) - 1)) +           \
       (UINT64_C(1) << (length + (k))) - (UINT64_C(1) << (k));        \
    val >>= length + (k);                                             \
    length += length + 1 + (k);                                       \
    UC64LE_READBITS(val, valI, preRead, cacheInt, EC);                \
  } while (0)

#define UC64LE_DECODEEXPGOLOMB_SMALL_NS(prefix, k, EC)           \
  do {                                                           \
    UC64LE_DECODEEXPGOLOMB_SMALL(prefix ## Val, prefix ## Compr, \
                 prefix ## PreRead, prefix ## CacheInt,          \
                 k, EC);                                         \
  } while (0)

#define UC64LE_DECODEEXPGOLOMB_SMALL_CTX(ctx, k, EC)             \
  do {                                                           \
    UC64LE_DECODEEXPGOLOMB_SMALL(ctx._val, ctx._valI,            \
                 ctx._preRead, ctx._cacheInt,                    \
                 k, EC);                                         \
  } while (0)

#define UC64LE_DECODEEXPGOLOMB_SMALL_APPLY(val, valI, preRead, cacheInt, \
                       k, EC, resop)                                     \
  do {                                                                   \
    length = __builtin_ctzl(val);                                        \
    val >>= length + 1;                                                  \
    resop (val & ((UINT64_C(1) << (length + (k))) - 1)) +                \
       (UINT64_C(1) << (length + (k))) - (UINT64_C(1) << (k));           \
    val >>= length + (k);                                                \
    length += length + 1 + (k);                                          \
    UC64LE_READBITS(val, valI, preRead, cacheInt, EC);                   \
  } while (0)


#define UC64LE_SKIPEXPGOLOMB(val, valI, preRead, cacheInt, k, EC) \
  do {                                                            \
    unsigned int olength = __builtin_ctzl(val);                   \
    length = olength + 1;                                         \
    if (__builtin_expect(length != 64, true)) {                   \
      val >>= length;                                             \
    } else {                                                      \
      val = 0;                                                    \
    }                                                             \
    if (__builtin_expect(olength * 2 + 1 + (k) > 64, false)) {    \
      UC64LE_READBITS(val, valI, preRead, cacheInt, EC);          \
      length = 0;                                                 \
    }                                                             \
    val >>= olength + (k);                                        \
    length += olength + (k);                                      \
    UC64LE_READBITS(val, valI, preRead, cacheInt, EC);            \
  } while (0)


#define UC64LE_SKIPEXPGOLOMB_NS(prefix, k, EC)           \
  do {                                                   \
    UC64LE_SKIPEXPGOLOMB(prefix ## Val, prefix ## Compr, \
             prefix ## PreRead, prefix ## CacheInt,      \
             k, EC);                                     \
  } while (0)

#define UC64LE_SKIPEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k,  \
                   EC)                                               \
  do {                                                               \
    length = __builtin_ctzl(val);                                    \
    val >>= length + 1;                                              \
    val >>= length + (k);                                            \
    length += length + 1 + (k);                                      \
    UC64LE_READBITS(val, valI, preRead, cacheInt, EC);               \
  } while (0)

#define UC64LE_SKIPEXPGOLOMB_SMALL_NS(prefix, k, EC)           \
  do {                                                         \
    UC64LE_SKIPEXPGOLOMB_SMALL(prefix ## Val, prefix ## Compr, \
                   prefix ## PreRead, prefix ## CacheInt,      \
                   k, EC);                                     \
  } while (0)

#define UC64LE_WRITEBITS(cacheInt, cacheFree, bufI, EC)      \
  do {                                                       \
    if (length >= cacheFree) {                               \
        cacheInt |= (data << (64 - cacheFree));              \
        *bufI++ = EC::bswap(cacheInt);                       \
        if (__builtin_expect(cacheFree != 64, true)) {       \
          data >>= cacheFree;                                \
        } else {                                             \
          data = 0;                                          \
        }                                                    \
        length -= cacheFree;                                 \
        cacheInt = 0;                                        \
        cacheFree = 64;                                      \
    }                                                        \
    if (length > 0) {                                        \
        uint64_t dataFragment =                              \
            (data & ::search::bitcompression::CodingTables:: \
            _intMask64[length]);                             \
        cacheInt |= (dataFragment << (64 - cacheFree));      \
        cacheFree -= length;                                 \
    }                                                        \
  } while (0)


#define UC64LE_WRITEBITS_NS(prefix, EC)                       \
  do {                                                        \
    UC64LE_WRITEBITS(prefix ## CacheInt, prefix ## CacheFree, \
                          prefix ## BufI, EC);                \
  } while (0)

#define UC64LE_WRITEBITS_CTX(ctx, EC)                   \
  do {                                                  \
    UC64LE_WRITEBITS(ctx ## cacheInt, ctx ## cacheFree, \
                   ctx ## valI, EC);                    \
  } while (0)

#define UC64_READBITS(val, valI, preRead, cacheInt, EC)  \
  do {                                                   \
    if (bigEndian) {                                     \
      UC64BE_READBITS(val, valI, preRead, cacheInt, EC); \
    } else {                                             \
      UC64LE_READBITS(val, valI, preRead, cacheInt, EC); \
    }                                                    \
  } while (0)

#define UC64_READBITS_NS(prefix, EC)                     \
  UC64_READBITS(prefix ## Val, prefix ## Compr,          \
          prefix ## PreRead, prefix ## CacheInt, EC)

#define UC64_READBITS_CTX(ctx, EC)                       \
  UC64_READBITS(ctx._val, ctx._valI,                     \
          ctx._preRead, ctx._cacheInt, EC)


#define UC64_SETUPBITS(bitOffset, val, valI, preRead, cacheInt, EC)  \
  do {                                                               \
    if (bigEndian) {                                                 \
      UC64BE_SETUPBITS(bitOffset, val, valI, preRead, cacheInt, EC); \
    } else {                                                         \
      UC64LE_SETUPBITS(bitOffset, val, valI, preRead, cacheInt, EC); \
    }                                                                \
  } while (0)

#define UC64_SETUPBITS_NS(ns, comprData, bitOffset, EC) \
    ns ## Compr = comprData;                            \
    UC64_SETUPBITS((bitOffset), ns ## Val, ns ## Compr, \
           ns ## PreRead, ns ## CacheInt, EC);

#define UC64_SETUPBITS_CTX(ctx, comprData, bitOffset, EC) \
    ctx._valI = comprData;                                \
    UC64_SETUPBITS((bitOffset), ctx._val, ctx._valI,      \
           ctx._preRead, ctx._cacheInt, EC);

#define UC64_DECODEEXPGOLOMB(val, valI, preRead, cacheInt, k, EC)  \
  do {                                                             \
    if (bigEndian) {                                               \
      UC64BE_DECODEEXPGOLOMB(val, valI, preRead, cacheInt, k, EC); \
    } else {                                                       \
      UC64LE_DECODEEXPGOLOMB(val, valI, preRead, cacheInt, k, EC); \
    }                                                              \
  } while (0)

#define UC64_DECODEEXPGOLOMB_NS(prefix, k, EC)           \
  do {                                                   \
    UC64_DECODEEXPGOLOMB(prefix ## Val, prefix ## Compr, \
             prefix ## PreRead, prefix ## CacheInt,      \
             k, EC);                                     \
  } while (0)

#define UC64_DECODEEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, EC)  \
  do {                                                                   \
    if (bigEndian) {                                                     \
      UC64BE_DECODEEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, EC); \
    } else {                                                             \
      UC64LE_DECODEEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, EC); \
    }                                                                    \
  } while (0)

#define UC64_DECODEEXPGOLOMB_SMALL_NS(prefix, k, EC)           \
  do {                                                         \
    UC64_DECODEEXPGOLOMB_SMALL(prefix ## Val, prefix ## Compr, \
                   prefix ## PreRead, prefix ## CacheInt,      \
                   k, EC);                                     \
  } while (0)

#define UC64_DECODEEXPGOLOMB_SMALL_CTX(ctx, k, EC)  \
  do {                                              \
    UC64_DECODEEXPGOLOMB_SMALL(ctx._val, ctx._valI, \
                   ctx._preRead, ctx._cacheInt,     \
                   k, EC);                          \
  } while (0)

#define UC64_DECODEEXPGOLOMB_SMALL_APPLY(val, valI, preRead, cacheInt, \
                       k, EC, resop)                                   \
  do {                                                                 \
    if (bigEndian) {                                                   \
      UC64BE_DECODEEXPGOLOMB_SMALL_APPLY(val, valI, preRead, cacheInt, \
                     k, EC, resop);                                    \
    } else {                                                           \
      UC64LE_DECODEEXPGOLOMB_SMALL_APPLY(val, valI, preRead, cacheInt, \
                     k, EC, resop);                                    \
    }                                                                  \
  } while (0)

#define UC64_SKIPEXPGOLOMB(val, valI, preRead, cacheInt, k, EC)  \
  do {                                                           \
    if (bigEndian) {                                             \
      UC64BE_SKIPEXPGOLOMB(val, valI, preRead, cacheInt, k, EC); \
    } else {                                                     \
      UC64LE_SKIPEXPGOLOMB(val, valI, preRead, cacheInt, k, EC); \
    }                                                            \
  } while (0)

#define UC64_SKIPEXPGOLOMB_NS(prefix, k, EC)           \
  do {                                                 \
    UC64_SKIPEXPGOLOMB(prefix ## Val, prefix ## Compr, \
               prefix ## PreRead, prefix ## CacheInt,  \
               k, EC);                                 \
  } while (0)

#define UC64_SKIPEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k,      \
                 EC)                                                   \
  do {                                                                 \
    if (bigEndian) {                                                   \
      UC64BE_SKIPEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, EC); \
    } else {                                                           \
      UC64LE_SKIPEXPGOLOMB_SMALL(val, valI, preRead, cacheInt, k, EC); \
    }                                                                  \
  } while (0)

#define UC64_SKIPEXPGOLOMB_SMALL_NS(prefix, k, EC)           \
  do {                                                       \
    UC64_SKIPEXPGOLOMB_SMALL(prefix ## Val, prefix ## Compr, \
                 prefix ## PreRead, prefix ## CacheInt,      \
                 k, EC);                                     \
  } while (0)

#define UC64_WRITEBITS(cacheInt, cacheFree, bufI, EC)  \
  do {                                                 \
    if (bigEndian) {                                   \
      UC64BE_WRITEBITS(cacheInt, cacheFree, bufI, EC); \
    } else {                                           \
      UC64LE_WRITEBITS(cacheInt, cacheFree, bufI, EC); \
    }                                                  \
  } while (0)


#define UC64_WRITEBITS_NS(prefix, EC)                       \
  do {                                                      \
    UC64_WRITEBITS(prefix ## CacheInt, prefix ## CacheFree, \
                   prefix ## BufI, EC);                     \
  } while (0)

#define UC64_WRITEBITS_CTX(ctx, EC)                   \
  do {                                                \
    UC64_WRITEBITS(ctx ## cacheInt, ctx ## cacheFree, \
                   ctx ## valI, EC);                  \
  } while (0)

#define UC64_ENCODECONTEXT(prefix) \
  uint64_t *prefix ## BufI;        \
  uint64_t prefix ## CacheInt;     \
  uint32_t prefix ## CacheFree;

#define UC64_ENCODECONTEXT_CONSTRUCTOR(prefix, ctx) \
  uint64_t *prefix ## BufI = ctx ## valI;           \
  uint64_t prefix ## CacheInt = ctx ## cacheInt;    \
  uint32_t prefix ## CacheFree = ctx ## cacheFree;

#define UC64_ENCODECONTEXT_LOAD(prefix, ctx) \
  prefix ## BufI = ctx ## valI;              \
  prefix ## CacheInt = ctx ## cacheInt;      \
  prefix ## CacheFree = ctx ## cacheFree;

#define UC64_ENCODECONTEXT_LOAD_PARTIAL(prefix, ctx) \
  prefix ## BufI = ctx ## valI;

#define UC64_ENCODECONTEXT_STORE(prefix, ctx) \
  ctx ## valI = prefix ## BufI;               \
  ctx ## cacheInt = prefix ## CacheInt;       \
  ctx ## cacheFree = prefix ## CacheFree;

#define UC64_ENCODECONTEXT_STORE_PARTIAL(prefix, ctx) \
  ctx ## valI = prefix ## BufI;


class EncodeContext64Base : public search::ComprFileEncodeContext
{
public:
    enum Constants {
        END_BUFFER_SAFETY = 4
    };

    using UnitType = uint64_t;

    // Pointers to compressed data
    uint64_t *_valI;
    const uint64_t *_valE;

    // Cached integers

    // _cacheInt is the second level of integer cache. It holds the
    // next bits (_cacheFree bits of this integer is free)
    uint64_t _cacheInt;
    uint32_t _cacheFree;

    // File position for start of buffer minus byte address of start of buffer
    // plus sizeof uint64_t.  Then shifted left by 3 to represent bits.
    uint64_t _fileWriteBias;

    EncodeContext64Base()
        : search::ComprFileEncodeContext(),
          _valI(nullptr),
          _valE(nullptr),
          _cacheInt(0),
          _cacheFree(64),
          _fileWriteBias(64)
    { }

    EncodeContext64Base(const EncodeContext64Base &other)
        : search::ComprFileEncodeContext(other),
          _valI(other._valI),
          _valE(other._valE),
          _cacheInt(other._cacheInt),
          _cacheFree(other._cacheFree),
          _fileWriteBias(other._fileWriteBias)
    { }

    ~EncodeContext64Base() = default;

    EncodeContext64Base &
    operator=(const EncodeContext64Base &rhs)
    {
        search::ComprFileEncodeContext::operator=(rhs);
        _valI = rhs._valI;
        _valE = rhs._valE;
        _cacheInt = rhs._cacheInt;
        _cacheFree = rhs._cacheFree;
        _fileWriteBias = rhs._fileWriteBias;
        return *this;
    }

    /**
     * Get number of used units (e.g. _valI - start)
     */
    int getUsedUnits(const uint64_t * start) override {
        return _valI - start;
    }

    /**
     * Get normal full buffer size (e.g. _valE - start)
     */
    int getNormalMaxUnits(void *start) override {
        return _valE - static_cast<uint64_t *>(start);
    }

    /**
     * Adjust buffer after write (e.g. _valI, _fileWriteBias)
     */
    void
    afterWrite(search::ComprBuffer &cbuf, uint32_t remainingUnits, uint64_t bufferStartFilePos) override {
        _valI = cbuf.getComprBuf() + remainingUnits;
        _fileWriteBias = (bufferStartFilePos -
                          reinterpret_cast<unsigned long>(cbuf.getComprBuf()) +
                          sizeof(uint64_t)) << 3;
        adjustBufSize(cbuf);
    }

    /**
     * Adjust buffer size to align end of buffer.
     */
    void adjustBufSize(search::ComprBuffer &cbuf) override {
        uint64_t fileWriteOffset =
            (_fileWriteBias +
             ((reinterpret_cast<unsigned long>(cbuf.getComprBuf()) -
               sizeof(uint64_t)) << 3)) >> 3;
        _valE = cbuf.getAdjustedBuf(fileWriteOffset);
    }

    uint32_t getUnitByteSize() const override {
        return sizeof(uint64_t);
    }

    void setupWrite(search::ComprBuffer &cbuf) {
        _valI = cbuf.getComprBuf();

        _fileWriteBias = (sizeof(uint64_t) - reinterpret_cast<unsigned long>(cbuf.getComprBuf())) << 3;
        // Buffer for compressed data now has padding after it
        adjustBufSize(cbuf);
        _cacheInt = 0;
        _cacheFree = 64;
    }

    void reload(const EncodeContext64Base &other) {
        _valI = other._valI;
        _valE = other._valE;
        _cacheInt = other._cacheInt;
        _cacheFree = other._cacheFree;
        _fileWriteBias = other._fileWriteBias;
    }

    void pushBack(EncodeContext64Base &other) const {
        other._valI = _valI;
        other._cacheInt = _cacheInt;
        other._cacheFree = _cacheFree;
    }

    uint64_t getWriteOffset() const {
        return _fileWriteBias + (reinterpret_cast<unsigned long>(_valI) << 3) - _cacheFree;
    }

    void defineWriteOffset(uint64_t writeOffset) {
        _fileWriteBias = writeOffset -
                         (reinterpret_cast<unsigned long>(_valI) << 3) +
                         _cacheFree;
    }

    /*
     * Return max value that can be exp golomb encoded with our implementation
     * ot the encoding method. Handling of larger numbers would require changes
     * to both decode macros (making them slower) and encoding method (making
     * it slower).
     */
    static uint64_t maxExpGolombVal(uint32_t kValue) {
        return static_cast<uint64_t>(- (UINT64_C(1) << kValue) - 1);
    }

    /*
     * Return max value that can be exp golomb encoded within maxBits
     * using kValue encoding parameter.
     *
     * maxBits must be larger than kValue
     */
    static uint64_t maxExpGolombVal(uint32_t kValue, uint32_t maxBits) {
        if ((maxBits + kValue + 1) / 2 > 64) {
            return static_cast<uint64_t>(-1);
        }
        if ((maxBits + kValue + 1) / 2 == 64) {
            return static_cast<uint64_t>
                (- (UINT64_C(1) << kValue) - 1);
        }
        return static_cast<uint64_t>
            ((UINT64_C(1) << ((maxBits + kValue + 1) / 2)) -
             (UINT64_C(1) << kValue) - 1);
    }

};


template <bool bigEndian>
class EncodeContext64EBase : public EncodeContext64Base
{
public:
    static inline uint64_t
    bswap(uint64_t val);

    /**
     * Write bits
     *
     * @param data      The bits to be written to file.
     * @param length    The number of bits to be written to file.
     */
    void writeBits(uint64_t data, uint32_t length);

    /**
     * Flushes the last integer to disk if there are remaining bits left in
     * the _cacheInt. Padding of trailing 0-bits is automatically added.
     */
    void flush() {
        if (_cacheFree < 64) {
            *_valI++ = bswap(_cacheInt);
            _cacheInt = 0;
            _cacheFree = 64;
        }
    }

    void smallPadBits(uint32_t length) {
        if (length > 0) {
            writeBits(0, length);
        }
    }

    virtual void padBits(uint32_t length) {
        while (length > 64) {
            writeBits(0, 64);
            length -= 64;
        }
        smallPadBits(length);
    }

    void align(uint32_t alignment) {
        uint64_t length = (- getWriteOffset()) & (alignment - 1);
        padBits(length);
    }

    void alignDirectIO() { align(4096*8); }

    /*
     * Small alignment (max 64 bits alignment)
     */
    void smallAlign(uint32_t alignment) {
        uint64_t length = _cacheFree & (alignment - 1);
        smallPadBits(length);
    }
};


template <>
inline uint64_t
EncodeContext64EBase<true>::bswap(uint64_t val)
{
    return __builtin_bswap64(val);
}


template <>
inline uint64_t
EncodeContext64EBase<false>::bswap(uint64_t val)
{
    return val;
}

using EncodeContext64BEBase = EncodeContext64EBase<true>;

using EncodeContext64LEBase = EncodeContext64EBase<false>;


template<bool bigEndian>
class EncodeContext64 : public EncodeContext64EBase<bigEndian>
{
public:
    using BaseClass = EncodeContext64EBase<bigEndian>;
    using BaseClass::writeBits;

    /**
     * Calculate floor(log2(x))
     */
    static inline uint32_t
    asmlog2(uint64_t x)
    {
        return sizeof(uint64_t) * 8 - 1 - __builtin_clzl(x);
    }

    static inline uint64_t
    ffsl(uint64_t x)
    {
        return __builtin_ctzl(x);
    }

    /**
     * ExpGolomb-encode an integer
     * @param x     integer to be encoded (lowest value is 0).
     * @param k     k parameter
     *
     * Note: This method doesn't work when x > maxExpGolombVal(k).
     */
    void
    encodeExpGolomb(uint64_t x, uint32_t k)
    {
        if (bigEndian) {
            uint32_t log2qx2 = asmlog2((x >> k) + 1) * 2;
            uint64_t expGolomb = x + (UINT64_C(1) << k);

            if (log2qx2 < 64 - k) {
                writeBits(expGolomb, k + log2qx2 + 1);
            } else {
                writeBits(0, k + log2qx2 + 1 - 64);
                writeBits(expGolomb, 64);
            }
        } else {
            uint32_t log2q = asmlog2((x >> k) + 1);
            uint32_t log2qx2 = log2q * 2;
            uint64_t expGolomb = x + (UINT64_C(1) << k) -
                                 (UINT64_C(1) << (k + log2q));

            if (log2qx2 < 64 - k) {
                writeBits(((expGolomb << 1) | 1) << log2q, k + log2qx2 + 1);
            } else {
                writeBits(0, log2q);
                writeBits((expGolomb << 1) | 1, log2q + k + 1);
            }
        }
    }

    static uint32_t
    encodeExpGolombSpace(uint64_t x, uint32_t k)
    {
        return k + asmlog2((x >> k) + 1) * 2 + 1;
    }

    void
    encodeDExpGolomb(uint64_t x, uint32_t k)
    {
        if (x == 0) {
            writeBits(0, 1);
            return;
        }
        if (x == 1) {
            writeBits(bigEndian ? 2 : 1, 2);
            return;
        }
        writeBits(3, 2);
        encodeExpGolomb(x - 2, k);
    }

    static uint32_t
    encodeDExpGolombSpace(uint64_t x, uint32_t k)
    {
        if (x == 0) {
            return 1;
        }
        if (x == 1) {
            return 2;
        }
        return 2 + encodeExpGolombSpace(x, k);
    }

    void
    encodeD0ExpGolomb(uint64_t x, uint32_t k)
    {
        if (x == 0) {
            writeBits(0, 1);
            return;
        }
        writeBits(1, 1);
        encodeExpGolomb(x - 1, k);
    }

    static uint32_t
    encodeD0ExpGolombSpace(uint64_t x, uint32_t k)
    {
        if (x == 0) {
            return 1;
        }
        return 1 + encodeExpGolombSpace(x, k);
    }

    static uint64_t
    convertToUnsigned(int64_t val)
    {
        if (val < 0) {
            return ((- val) << 1) - 1;
        } else {
            return (val << 1);
        }
    }
};


using EncodeContext64BE = EncodeContext64<true>;

using EncodeContext64LE = EncodeContext64<false>;

class DecodeContext64Base : public search::ComprFileDecodeContext
{
private:
    DecodeContext64Base(const DecodeContext64Base &);

public:
    enum Constants {
        END_BUFFER_SAFETY = 4
    };

    // Pointers to compressed data
    const uint64_t *_valI;
    const uint64_t *_valE;
    const uint64_t *_realValE;

    // Cached integers

    // _val is the work-integer which is by convention always filled
    // with the next 64 bits (the first bit is #31)
    uint64_t _val;

    // _cacheInt is the second level of integer cache. It holds the
    // next bits (_preRead bits of this integer is valid)
    uint64_t _cacheInt;
    uint32_t _preRead;

    // File position for end of buffer minus byte address of end of buffer
    // minus sizeof uint64_t.  Then shifted left by 3 to represent bits.
    uint64_t _fileReadBias;
    search::ComprFileReadContext *_readContext;

    DecodeContext64Base()
        : search::ComprFileDecodeContext(),
          _valI(nullptr),
          _valE(reinterpret_cast<const uint64_t *>(PTRDIFF_MAX)),
          _realValE(nullptr),
          _val(0),
          _cacheInt(0),
          _preRead(0),
          _fileReadBias(0),
          _readContext(nullptr)
    {
    }


    DecodeContext64Base(const uint64_t *valI,
                        const uint64_t *valE,
                        const uint64_t *realValE,
                        uint64_t val,
                        uint64_t cacheInt,
                        uint32_t preRead)
        : search::ComprFileDecodeContext(),
          _valI(valI),
          _valE(valE),
          _realValE(realValE),
          _val(val),
          _cacheInt(cacheInt),
          _preRead(preRead),
          _fileReadBias(0),
          _readContext(nullptr)
    {
    }

    virtual ~DecodeContext64Base() = default;

    DecodeContext64Base &
    operator=(const DecodeContext64Base &rhs)
    {
        search::ComprFileDecodeContext::operator=(rhs);
        _valI = rhs._valI;
        _valE = rhs._valE;
        _realValE = rhs._realValE;
        _val = rhs._val;
        _cacheInt = rhs._cacheInt;
        _preRead = rhs._preRead;
        _fileReadBias = rhs._fileReadBias;
        _readContext = rhs._readContext;
        return *this;
    }

    /**
     *
     * Check if the chunk referenced by the decode context was the
     * last chunk in the file (e.g. _valE > _realValE)
     */
    bool lastChunk() const override { return _valE > _realValE; }

    /**
     * Check if we're at the end of the current chunk (e.g. _valI >= _valE)
     */
    bool endOfChunk() const override { return _valI >= _valE; }

    /**
     * Get remaining units in buffer (e.g. _realValE - _valI)
     */

    int32_t remainingUnits() const override { return _realValE - _valI; }

    /**
     * Get unit ptr (e.g. _valI) from decode context.
     */
    const void *getUnitPtr() const override { return _valI; }

    void afterRead(const void *start, size_t bufferUnits, uint64_t bufferEndFilePos, bool isMore) override {
        _valI = static_cast<const uint64_t *>(start);
        setEnd(bufferUnits, isMore);
        _fileReadBias = (bufferEndFilePos - reinterpret_cast<unsigned long>(_realValE + 1)) << 3;
    }

    uint64_t getBitPos(int bitOffset, uint64_t bufferEndFilePos) const override {
        int intOffset = _realValE - _valI;
        if (bitOffset == -1) {
            bitOffset = -64 - _preRead;
        }
        return (bufferEndFilePos << 3) - (static_cast<uint64_t>(intOffset) << 6) + bitOffset;
    }

    uint64_t getReadOffset() const {
        return _fileReadBias + (reinterpret_cast<unsigned long>(_valI) << 3) - _preRead;
    }

    void defineReadOffset(uint64_t readOffset) {
        _fileReadBias  = readOffset -
                         (reinterpret_cast<unsigned long>(_valI) << 3) +
                         _preRead;
    }

    uint64_t getBitPosV() const override { return getReadOffset(); }

    void adjUnitPtr(int newRemainingUnits) override {
        _valI = _realValE - newRemainingUnits;
    }

    void emptyBuffer(uint64_t newBitPosition) override {
        _fileReadBias = newBitPosition;
        _valI = nullptr;
        _valE = nullptr;
        _realValE = nullptr;
        _preRead = 0;
    }

    uint32_t getUnitByteSize() const override { return sizeof(uint64_t); }

    /**
     * Set the end of the buffer
     * @param  unitCount   Number of bytes in buffer
     * @param  moreData    Set if there is more data available
     */
    void setEnd(unsigned int unitCount, bool moreData) {
        _valE = _realValE = _valI + unitCount;
        if (moreData) {
            _valE -= END_BUFFER_SAFETY;
        } else {
            _valE += END_BUFFER_SAFETY;
        }
    }

    const uint64_t *getCompr() const {
        return (_preRead == 0) ? (_valI - 1) : (_valI - 2);
    }

    int getBitOffset() const {
        return (_preRead == 0) ? 0 : 64 - _preRead;
    }

    static int64_t convertToSigned(uint64_t val) {
        if ((val & 1) != 0) {
            return - (val >> 1) - 1;
        } else {
            return (val >> 1);
        }
    }

    void setReadContext(search::ComprFileReadContext *readContext) {
        _readContext = readContext;
    }

    void readComprBuffer() {
        _readContext->readComprBuffer();
    }
    void readComprBufferIfNeeded() {
        if (__builtin_expect(_valI >= _valE, false)) {
            readComprBuffer();
        }
    }
    virtual uint64_t readBits(uint32_t length) = 0;
    virtual void align(uint32_t alignment) = 0;
    virtual uint64_t decode_exp_golomb(int k) = 0;
    void readBytes(uint8_t *buf, size_t len);
    uint32_t readHeader(vespalib::GenericHeader &header, int64_t fileSize);
};


template <bool bigEndian>
class DecodeContext64 : public DecodeContext64Base
{
private:
    DecodeContext64(const DecodeContext64 &);

public:
    using EC = EncodeContext64<bigEndian>;

    DecodeContext64() = default;


    DecodeContext64(const uint64_t *compr, int bitOffset)
        : DecodeContext64Base(compr + 1,
                              reinterpret_cast<const uint64_t *>(PTRDIFF_MAX),
                              nullptr,
                              0,
                              EC::bswap(*compr),
                              64 - bitOffset)
    {
        uint32_t length = 64;
        UC64_READBITS(_val, _valI, _preRead, _cacheInt, EC);
    }

    /*
     * Setup decode context without read context, all data is in memory.
     * Assumes that last word is fully readable, and that some extra
     * data beyond is available, to avoid issues when prefetching bits
     * into two registers (_val and _cacheInt).
     */
    DecodeContext64(const uint64_t *compr, int bitOffset, uint64_t bitLength)
        : DecodeContext64Base(compr + 1,
                              nullptr,
                              nullptr,
                              0,
                              EC::bswap(*compr),
                              64 - bitOffset)
    {
        uint32_t length = 64;
        UC64_READBITS(_val, _valI, _preRead, _cacheInt, EC);
        _realValE = compr + (bitOffset + bitLength + 63) / 64;
        _valE = _realValE + END_BUFFER_SAFETY;
    }

    DecodeContext64 &
    operator=(const DecodeContext64 &rhs)
    {
        DecodeContext64Base::operator=(rhs);
        return *this;
    }

    /**
     * Read [length] bits from a bitstream
     *
     * @param length   Number of bits to read (0 < length < 64)
     * @param val      Current integer holding bits
     * @param cacheInt 2nd level integer cache
     * @param preRead  Number of valid bits in cacheInt
     * @param valI     Pointer to next integer in bitstream
     */
    static void
    ReadBits(unsigned int length, uint64_t &val,
             uint64_t &cacheInt, unsigned int &preRead,
             const uint64_t * &valI)
    {
        if (length <= preRead) {
            if (bigEndian) {
                val |= ((cacheInt >> (preRead - length)) &
                        CodingTables::_intMask64[length]);
            } else {
                val |= ((cacheInt << (preRead - length)) &
                        CodingTables::_intMask64le[length]);
            }
            preRead -= length;
            return;
        }

        if (preRead > 0) {
            length -= preRead;
            if (bigEndian) {
                val |= ((cacheInt &
                         CodingTables::_intMask64[preRead]) << length);
            } else {
                val |= ((cacheInt &
                         CodingTables::_intMask64le[preRead]) >> length);
            }
        }

        cacheInt = EC::bswap(*valI++);
        preRead = 64 - length;
        if (bigEndian) {
            val |= (cacheInt >> preRead);
        } else {
            val |= (cacheInt << preRead);
        }
    };

    void skipBits(int bits) override {
        readComprBufferIfNeeded();
        while (bits >= 64) {
            _val = 0;
            ReadBits(64, _val, _cacheInt, _preRead, _valI);
            bits -= 64;
            readComprBufferIfNeeded();
        }
        if (bits > 0) {
            if (bigEndian) {
                _val <<= bits;
            } else {
                _val >>= bits;
            }
            ReadBits(bits, _val, _cacheInt, _preRead, _valI);
            readComprBufferIfNeeded();
        }
    }

    /**
     * Setup for bitwise reading.
     */
    void setupBits(int bitOffset) override {
        unsigned int length;
        UC64_SETUPBITS(bitOffset, _val, _valI, _preRead, _cacheInt, EC);
    }

    void setPosition(Position pos) {
        _valI = pos.getOccurences();
        setupBits(pos.getBitOffset());
    }

    /**
     * Used by iterators when switching from bitwise to bytewise decoding.
     */
    const uint8_t *
    getByteCompr() const
    {
        assert((_preRead & 7) == 0);
        return reinterpret_cast<const uint8_t *>(getCompr()) +
            (getBitOffset() >> 3);
    }

    /**
     * Used by iterators when switching from bytewise to bitwise decoding.
     */
    void
    setByteCompr(const uint8_t *bCompr)
    {
        int byteOffset = reinterpret_cast<unsigned long>(bCompr) & 7;
        _valI = reinterpret_cast<const uint64_t *>(bCompr - byteOffset);
        setupBits(byteOffset * 8);
    }

    uint64_t
    readBits(uint32_t length) override
    {
        uint64_t res;
        if (length < 64) {
            if (bigEndian) {
                res = _val >> (64 - length);
                _val <<= length;
            } else {
                res = _val & CodingTables::_intMask64[length];
                _val >>= length;
            }
        } else {
            res = _val;
            _val = 0;
        }
        UC64_READBITS(_val, _valI, _preRead, _cacheInt, EC);
        readComprBufferIfNeeded();
        return res;
    }

    uint64_t decode_exp_golomb(int k) override {
        uint32_t length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB(_val, _valI, _preRead, _cacheInt, k, EC);
        readComprBufferIfNeeded();
        return val64;
    }

    void
    align(uint32_t alignment) override
    {
        readComprBufferIfNeeded();
        uint64_t pad = (- getReadOffset()) & (alignment - 1);
        while (pad > 64) {
            (void) readBits(64);
            pad -= 64;
            readComprBufferIfNeeded();
        }
        if (pad > 0) {
            (void) readBits(pad);
        }
        readComprBufferIfNeeded();
    }

    /*
     * Small alignment (max 64 bits alignment)
     */
    void
    smallAlign(uint32_t alignment)
    {
        uint64_t pad = _preRead & (alignment - 1);
        if (pad > 0) {
            (void) readBits(pad);
        }
    }
};

using DecodeContext64BE = DecodeContext64<true>;

using DecodeContext64LE = DecodeContext64<false>;

template <bool bigEndian>
class FeatureDecodeContext : public DecodeContext64<bigEndian>
{
public:
    using ParentClass = DecodeContext64<bigEndian>;
    using DocIdAndFeatures = index::DocIdAndFeatures;
    using PostingListParams = index::PostingListParams;
    using ParentClass::_val;
    using ParentClass::_valI;
    using ParentClass::_valE;
    using ParentClass::_realValE;
    using ParentClass::_cacheInt;
    using ParentClass::_preRead;
    using ParentClass::getReadOffset;
    using ParentClass::getCompr;
    using ParentClass::getBitOffset;
    using ParentClass::readBits;
    using ParentClass::ReadBits;
    using ParentClass::readComprBuffer;
    using ParentClass::readComprBufferIfNeeded;
    using ParentClass::readHeader;
    using ParentClass::readBytes;

    FeatureDecodeContext() = default;

    FeatureDecodeContext(const uint64_t *compr, int bitOffset)
        : ParentClass(compr, bitOffset)
    {
    }

    FeatureDecodeContext(const uint64_t *compr, int bitOffset, uint64_t bitLength)
        : ParentClass(compr, bitOffset, bitLength)
    {
    }

    virtual void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix);

    virtual const vespalib::string & getIdentifier() const;
    virtual void readFeatures(DocIdAndFeatures &features);
    virtual void skipFeatures(unsigned int count);
    virtual void unpackFeatures(const search::fef::TermFieldMatchDataArray &matchData, uint32_t docId);
    virtual void setParams(const PostingListParams &params);
    virtual void getParams(PostingListParams &params) const;
};

using FeatureDecodeContextBE = FeatureDecodeContext<true>;

using FeatureDecodeContextLE = FeatureDecodeContext<false>;

template <bool bigEndian>
class FeatureEncodeContext : public EncodeContext64<bigEndian>
{
public:
    search::ComprFileWriteContext *_writeContext;
    using ParentClass = EncodeContext64<bigEndian>;
    using DocIdAndFeatures = index::DocIdAndFeatures;
    using PostingListParams = index::PostingListParams;
    using ParentClass::_cacheInt;
    using ParentClass::_cacheFree;
    using ParentClass::smallPadBits;

public:
    FeatureEncodeContext()
        : ParentClass(),
          _writeContext(nullptr)
    {
    }

    FeatureEncodeContext &
    operator=(const FeatureEncodeContext &rhs)
    {
        ParentClass::operator=(rhs);
        _writeContext = rhs._writeContext;
        return *this;
    }

    void setWriteContext(search::ComprFileWriteContext *writeContext) {
        _writeContext = writeContext;
    }

    using ParentClass::asmlog2;
    using ParentClass::_valI;
    using ParentClass::_valE;

    static int
    calcDocIdK(uint32_t numDocs, uint32_t docIdLimit)
    {
        uint32_t avgDelta = docIdLimit / (numDocs + 1);
        uint32_t docIdK = (avgDelta < 4) ? 1 : (asmlog2(avgDelta));
        return docIdK;
    }

    using ParentClass::writeBits;

    void writeBits(const uint64_t *bits, uint32_t bitOffset, uint32_t bitLength);
    void writeBytes(vespalib::ConstArrayRef<char> buf);
    void writeString(vespalib::stringref buf);
    virtual void writeHeader(const vespalib::GenericHeader &header);

    void writeComprBufferIfNeeded() {
        if (_valI >= _valE) {
            _writeContext->writeComprBuffer(false);
        }
    }

    void writeComprBuffer() {
        _writeContext->writeComprBuffer(true);
    }

    void padBits(uint32_t length) override {
        while (length > 64) {
            writeBits(0, 64);
            length -= 64;
            writeComprBufferIfNeeded();
        }
        smallPadBits(length);
        writeComprBufferIfNeeded();
    }

    virtual void readHeader(const vespalib::GenericHeader &header, const vespalib::string &prefix);
    virtual void writeHeader(vespalib::GenericHeader &header, const vespalib::string &prefix) const;
    virtual const vespalib::string &getIdentifier() const;
    virtual void writeFeatures(const DocIdAndFeatures &features);
    virtual void setParams(const PostingListParams &params);
    virtual void getParams(PostingListParams &params) const;
};

using FeatureEncodeContextBE = FeatureEncodeContext<true>;

using FeatureEncodeContextLE = FeatureEncodeContext<false>;

extern template class FeatureDecodeContext<true>;
extern template class FeatureDecodeContext<false>;

extern template class FeatureEncodeContext<true>;
extern template class FeatureEncodeContext<false>;

}
