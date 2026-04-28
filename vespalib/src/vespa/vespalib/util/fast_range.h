// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib {

/**
 * Fast and fair mapping of _statistically random_ u32 and u64 values into an
 * arbitrary range via the Lemire reduction method(tm). For random variables
 * this is a fast alternative to (non-power of 2) modulo calculations which
 * only uses one multiplication and a bit-shift.
 *
 * Note that the output of this mapping is _not at all_ equal to the modulo
 * operation, and trying to use it as a direct substitution will inevitably
 * end in tears. This also means it can't be used for hash tables that use
 * identity (aka. no-op, aka. "I'm sure it will be fine, yolo") hashing of
 * integers, as all sufficiently low numbers will end up in the same bucket!
 *
 * See https://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
 */

// Returns an unspecified (but fair) mapping of the random u32 variable `x` into the u32 range [0, `n`]
[[nodiscard]] constexpr uint32_t map_random_to_range(const uint32_t x, const uint32_t n) noexcept {
    return (static_cast<uint64_t>(x) * static_cast<uint64_t>(n)) >> 32;
}

// Returns an unspecified (but fair) mapping of the random u64 variable `x` into the u64 range [0, `n`]
[[nodiscard]] constexpr uint64_t map_random_to_range(const uint64_t x, const uint64_t n) noexcept {
    // The compiler understands this pattern well enough that it will use the
    // native instruction and/or register for extracting the top 64 bits of the
    // multiplication, making this no more costly than a normal multiplication.
    //
    // On x64, this will do a regular (unsigned) MUL (which places high bits in
    // RDX and low bits in RAX) and just use the RDX register result verbatim.
    //
    // On AArch64 it will directly generate an UMULH (multiply, return high bits)
    // instruction.
    //
    // Godbolt example: https://godbolt.org/z/G75Gzj1o4
    return (static_cast<__uint128_t>(x) * n) >> 64;
}

} // namespace vespalib
