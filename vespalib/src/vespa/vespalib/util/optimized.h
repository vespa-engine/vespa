// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

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
    static int msbIdx(unsigned int v);
    static int msbIdx(unsigned long v);
    static int msbIdx(unsigned long long v);
    static int lsbIdx(unsigned int v);
    static int lsbIdx(unsigned long v);
    static int lsbIdx(unsigned long long v);
    static constexpr int popCount(unsigned int v) { return __builtin_popcount(v); }
    static constexpr int popCount(unsigned long v) { return __builtin_popcountl(v); }
    static constexpr int popCount(unsigned long long v) { return __builtin_popcountll(v); }
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
inline int Optimized::msbIdx(unsigned int v) {
    unsigned int result;
    __asm __volatile("bsrl %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::lsbIdx(unsigned int v) {
    unsigned int result;
    __asm __volatile("bsfl %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::msbIdx(unsigned long v) {
    unsigned long result;
    __asm __volatile("bsrq %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::lsbIdx(unsigned long v) {
    unsigned long result;
    __asm __volatile("bsfq %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::msbIdx(unsigned long long v) {
    unsigned long long result;
    __asm __volatile("bsrq %0,%0" : "=r" (result) : "0" (v));
    return result;
}
inline int Optimized::lsbIdx(unsigned long long v) {
    unsigned long long result;
    __asm __volatile("bsfq %0,%0" : "=r" (result) : "0" (v));
    return result;
}
#else
inline int Optimized::msbIdx(unsigned int v) { return v ? sizeof(unsigned int) * 8 - 1 - __builtin_clz(v) : 0; }
inline int Optimized::msbIdx(unsigned long v) { return v ? sizeof(unsigned long) * 8 - 1 - __builtin_clzl(v) : 0; }
inline int Optimized::msbIdx(unsigned long long v) { return v ? sizeof(unsigned long long) * 8 - 1 - __builtin_clzll(v) : 0; }
inline int Optimized::lsbIdx(unsigned int v) { return v ? __builtin_ctz(v) : 0; }
inline int Optimized::lsbIdx(unsigned long v) { return v ? __builtin_ctzl(v) : 0; }
inline int Optimized::lsbIdx(unsigned long long v) { return v ? __builtin_ctzll(v) : 0; }
#endif

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

}

