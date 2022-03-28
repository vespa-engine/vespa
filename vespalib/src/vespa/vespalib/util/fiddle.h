// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cassert>

namespace vespalib {
namespace bits {

//-----------------------------------------------------------------------------

/**
 * Mix the prefix of one number with the suffix of another.
 *
 * @return mixed value
 * @param prefix the most significant bits are taken from this value
 * @param suffix the least significant bits are taken from this value
 * @param prefix_bits how many bits to take from the prefix
 **/
uint32_t mix(uint32_t prefix, uint32_t suffix, uint32_t prefix_bits) {
    if (prefix_bits == 0) {
        return suffix;
    }
    if (prefix_bits >= 32) {
        return prefix;
    }
    uint32_t suffix_mask = (1u << (32u - prefix_bits)) - 1u;
    uint32_t prefix_mask = (0u - 1u) - suffix_mask;
    return (prefix & prefix_mask) | (suffix & suffix_mask);
}

//-----------------------------------------------------------------------------

/**
 * Find the number of leading zero bits in the given number.
 *
 * @return the number of leading zeros (0-32)
 * @param value the value to inspect.
 **/
uint32_t leading_zeros(uint32_t value) {
    if (value == 0) {
        return 32;
    }
    uint32_t n = 0;
    if (value <= 0x0000ffff) { n += 16; value <<= 16; }
    if (value <= 0x00FFffff) { n +=  8; value <<=  8; }
    if (value <= 0x0FFFffff) { n +=  4; value <<=  4; }
    if (value <= 0x3FFFffff) { n +=  2; value <<=  2; }
    if (value <= 0x7FFFffff) { n +=  1; }
    return n;
}

//-----------------------------------------------------------------------------

/**
 * Split the inclusive range [min, max] into two separate adjacent
 * ranges such that the highest differing bit between min and max will
 * be 0 for both endpoints in the first range and 1 in the second.
 * The split ranges will be [min, first_max] and [last_min, max] after
 * calling this function. A higher return value indicate a coarser
 * split. Note that when this function returns 0, you end up with 2
 * copies of the same identity range.
 *
 * @param min original minimum endpoint
 * @param max original maximum endpoint
 * @param first_max maximum endpoint of first subrange
 * @param last_min miniumum endpoint of last subrange
 * @return the number of bits in min and max not part of a common prefix
 **/
uint32_t split_range(uint32_t min, uint32_t max,
                     uint32_t &first_max, uint32_t &last_min)
{
    assert(max >= min);
    uint32_t prefix = leading_zeros(min ^ max);
    first_max = mix(min, 0xFFFFffff, prefix + 1);
    last_min  = mix(max, 0x00000000, prefix + 1);
    return (32 - prefix);
}

//-----------------------------------------------------------------------------

} // namespace bits
} // namespace vespalib

