// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <stdint.h>

namespace vespalib {

/**
 * @brief Optimized instructions class
 *
 * Here are fast handcoded assembly functions for doing some special
 * low level functions that can be carried out very fast by special instructions.
 * Currently only implemented for GCC on i386 and x86_64 platforms.
 **/
class Optimized
{
public:
    static int msbIdx(uint32_t v);
    static int msbIdx(uint64_t v);
    static int lsbIdx(uint32_t v);
    static int lsbIdx(uint64_t v);
    static int popCount(uint32_t v) { return __builtin_popcount(v); }
    static int popCount(uint64_t v) { return __builtin_popcountl(v); }
};

/**
 * @fn int Optimized::msbIdx(uint32_t v)
 * @brief Quickly find most significant bit.
 *
 * Finds the postion of the most significant '1'.
 * @param v is the value to search
 * @return index [0-31] of msb, 0 if none.
 **/

/**
 * @fn int Optimized::msbIdx(uint64_t v)
 * @brief Quickly find most significant bit.
 *
 * Finds the postion of the most significant '1'.
 * @param v is the value to search
 * @return index [0-63] of msb, 0 if none.
 **/

/**
 * @fn int Optimized::lsbIdx(uint32_t v)
 * @brief Quickly find least significant bit.
 *
 * Finds the postion of the least significant '1'.
 * @param v is the value to search
 * @return index [0-31] of lsb, 0 if none.
 **/

/**
 * @fn int Optimized::lsbIdx(uint64_t v)
 * @brief Quickly find least significant bit.
 *
 * Finds the postion of the least significant '1'.
 * @param v is the value to search
 * @return index [0-63] of lsb, 0 if none.
 **/

#ifdef __x86_64__
inline int Optimized::msbIdx(uint32_t v) {
    int32_t result;
    __asm __volatile("bsrl %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::lsbIdx(uint32_t v) {
    int32_t result;
    __asm __volatile("bsfl %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::msbIdx(uint64_t v) {
    int64_t result;
    __asm __volatile("bsrq %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::lsbIdx(uint64_t v) {
    int64_t result;
    __asm __volatile("bsfq %0,%0" : "=r" (result) : "0" (v));
    return result;
}
#else
inline int Optimized::msbIdx(uint32_t v) { return v ? 31 - __builtin_clz(v) : 0; }
inline int Optimized::msbIdx(uint64_t v) { return v ? 63 - __builtin_clzl(v) : 0; }
inline int Optimized::lsbIdx(uint32_t v) { return v ? 31 - __builtin_ctz(v) : 0; }
inline int Optimized::lsbIdx(uint64_t v) { return v ? 63 - __builtin_ctzl(v) : 0; }
#endif

}

