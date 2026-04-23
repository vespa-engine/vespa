// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <limits>

namespace vespalib {

/**
 * SplitMix64 by Sebastiano Vigna used to expand a single u64 seed value into
 * multiple, statistically uncorrelated seed values.
 *
 * Adapted from https://prng.di.unimi.it/splitmix64.c (public domain)
 */
class splitmix64 {
    uint64_t _x;
public:
    constexpr explicit splitmix64(const uint64_t x) noexcept : _x(x) {}

    [[nodiscard]] constexpr uint64_t operator()() noexcept {
        uint64_t z = (_x += 0x9e3779b97f4a7c15);
        z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9;
        z = (z ^ (z >> 27)) * 0x94d049bb133111eb;
        return z ^ (z >> 31);
    }
};

/**
 * This is an implementation of the xoshiro256++ (xor/shift/rotate) all-purpose
 * pseudo-random number generator algorithm by David Blackman and Sebastiano Vigna.
 *
 * As the name implies it has 256 bits of internal state, which is vastly less than
 * the Mersenne Twister PRNG (std::mt19937) at 5000 (!) bytes, while at the same
 * time being faster and having better statistical qualities.
 *
 * Strongly consider using this class instead of the STL _deterministic_ PRNGs,
 * as these are mostly all linear congruential or Mersenne Twister-based engines.
 *
 * This type satisfies the C++20 uniform_random_bit_generator concept.
 *
 * Important: this is _not_ a cryptographically secure random number generator.
 *
 * Adapted from https://prng.di.unimi.it/xoshiro256plusplus.c (public domain).
 */
class Xoshiro256PlusPlusPrng {
    uint64_t _s[4];
public:
    using result_type = uint64_t;

    // Default-seeded PRNG
    constexpr Xoshiro256PlusPlusPrng() noexcept {
        // Nothing up my sleeve: the default PRNG init state is the SHA-256 of
        // the Most Blessed String "XL-1 og rett i koppen".
        _s[0] = 0x882648ada6c255b9;
        _s[1] = 0x5d986b9abb1aa746;
        _s[2] = 0x63e26405f03bc2b0;
        _s[3] = 0x5642ca7dc7c0482f;
    }

    // Explicitly 256-bit seeded PRNG. The seed parts should ideally have high
    // entropy and be statistically uncorrelated, such as from a CSPRNG source.
    constexpr Xoshiro256PlusPlusPrng(const uint64_t s0, const uint64_t s1,
                                     const uint64_t s2, const uint64_t s3) noexcept {
        _s[0] = s0;
        _s[1] = s1;
        _s[2] = s2;
        _s[3] = s3;
    }

    // Explicitly 64-bit seeded PRNG. The seed is internally expanded to 256 bits
    // in a way that statistically decorrelates the individual state components.
    constexpr explicit Xoshiro256PlusPlusPrng(const uint64_t s) noexcept {
        seed(s);
    }

    constexpr void seed(const uint64_t seed) noexcept {
        // Expand single u64 seed into 256 bits of pseudo-randomness
        splitmix64 mixer(seed);
        _s[0] = seed;
        _s[1] = mixer();
        _s[2] = mixer();
        _s[3] = mixer();
    }

    [[nodiscard]] static constexpr result_type min() noexcept { return 0; }
    [[nodiscard]] static constexpr result_type max() noexcept { return std::numeric_limits<result_type>::max(); }

    // Returns the next pseudo-random uniformly distributed number in the range [0, 2**64-1]
    [[nodiscard]] constexpr uint64_t operator()() noexcept {
        const uint64_t result = rotl(_s[0] + _s[3], 23) + _s[0];
        const uint64_t t = _s[1] << 17;

        _s[2] ^= _s[0];
        _s[3] ^= _s[1];
        _s[1] ^= _s[2];
        _s[0] ^= _s[3];

        _s[2] ^= t;

        _s[3] = rotl(_s[3], 45);

        return result;
    }

private:
    [[nodiscard]] static constexpr uint64_t rotl(const uint64_t x, const int k) noexcept {
        return (x << k) | (x >> (64 - k));
    }
};

} // namespace vespalib
