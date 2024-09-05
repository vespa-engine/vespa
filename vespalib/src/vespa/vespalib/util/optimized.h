// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <bit>

namespace vespalib {

/**
 * @brief Optimized instructions class
 **/
class Optimized
{
public:

    static constexpr int msbIdx(unsigned int v) noexcept { return v ? sizeof(unsigned int) * 8 - 1 - std::countl_zero(v) : 0; }
    static constexpr int msbIdx(unsigned long v) noexcept { return v ? sizeof(unsigned long) * 8 - 1 - std::countl_zero(v) : 0; }
    static constexpr int msbIdx(unsigned long long v) noexcept  { return v ? sizeof(unsigned long long) * 8 - 1 - std::countl_zero(v) : 0; }
    static constexpr int lsbIdx(unsigned int v) noexcept { return v ? std::countr_zero(v) : 0; }
    static constexpr int lsbIdx(unsigned long v) noexcept { return v ? std::countr_zero(v) : 0; }
    static constexpr int lsbIdx(unsigned long long v) noexcept { return v ? std::countr_zero(v) : 0; }
};

/**
 * @fn int Optimized::msbIdx(uint32_t v)
 * @brief Quickly find most significant bit.
 *
 * Finds the position of the most significant '1'.
 * @param v is the value to search
 * @return index [0-31] of msb, 0 if none.
 **/

/**
 * @fn int Optimized::msbIdx(uint64_t v)
 * @brief Quickly find most significant bit.
 *
 * Finds the position of the most significant '1'.
 * @param v is the value to search
 * @return index [0-63] of msb, 0 if none.
 **/

/**
 * @fn int Optimized::lsbIdx(uint32_t v)
 * @brief Quickly find least significant bit.
 *
 * Finds the position of the least significant '1'.
 * @param v is the value to search
 * @return index [0-31] of lsb, 0 if none.
 **/

/**
 * @fn int Optimized::lsbIdx(uint64_t v)
 * @brief Quickly find least significant bit.
 *
 * Finds the position of the least significant '1'.
 * @param v is the value to search
 * @return index [0-63] of lsb, 0 if none.
 **/

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

}

