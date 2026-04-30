// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <limits>
#include <random>

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

// Metafunction for widening an unsigned integer type to the next greater type.
template <typename> struct Widened;
template <> struct Widened<uint8_t>  { using type = uint16_t; };
template <> struct Widened<uint16_t> { using type = uint32_t; };
template <> struct Widened<uint32_t> { using type = uint64_t; };
template <> struct Widened<uint64_t> { using type = __uint128_t; };

template <typename T> using Widen = Widened<T>::type;

// Returns an unspecified (but fair) mapping of the random variable `x` into the range [0, `n`)
template <std::unsigned_integral T>
[[nodiscard]] constexpr T map_random_to_range(const T x, const T n) noexcept {
    // For 128-bit multiplies, the compiler understands this pattern well enough
    // that it will use the native instruction and/or register for extracting the
    // top 64 bits of the multiplication, making this no more costly than a normal
    // multiplication.
    //
    // On x64, this will do a regular (unsigned) MUL (which places high bits in
    // RDX and low bits in RAX) and just use the RDX register result verbatim.
    //
    // On AArch64 it will directly generate an UMULH (multiply, return high bits)
    // instruction.
    //
    // Godbolt example: https://godbolt.org/z/G75Gzj1o4
    return (static_cast<Widen<T>>(x) * n) >> (sizeof(T) * 8);
}

// Returns a random number in the range [from_incl, to_excl) generated using the
// supplied RNG instance `rng`.
// Precondition: to_excl > from_incl
template <std::unsigned_integral T, std::uniform_random_bit_generator Rng>
[[nodiscard]] T next_random_in_range(Rng& rng, const T from_incl, const T to_excl) noexcept {
    static_assert(Rng::min() == 0, "RNG output must cover entire value range");
    static_assert(Rng::max() >= std::numeric_limits<T>::max(), "RNG output must cover entire value range");
    return from_incl + map_random_to_range<T>(static_cast<T>(rng()), to_excl - from_incl);
}

} // namespace vespalib
