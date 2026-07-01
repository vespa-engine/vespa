// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <random>
#include <span>

namespace vespalib::quant {

template <typename> struct ToUintT;
template <> struct ToUintT<float> {
    using type = uint32_t;
};
template <> struct ToUintT<double> {
    using type = uint64_t;
};

template <std::floating_point T> using ToUint = ToUintT<T>::type;

/*
 * Pseudo-randomly flip the sign bits in-place of all floating point values contained in `v`.
 *
 * This operation is _invertible_. Assume `flip_sign_bits` takes in span V and PRNG state
 * foo, yielding V'. Calling `flip_sign_bits` with span V' and the _same_ PRNG state foo
 * will yield back the original V. Needless to say, the PRNG invocations must generate
 * bitwise exact identical output sequences across the two flip-calls for this to work.
 *
 * This will always invoke `prng()` ceil(v.size() / 64) times.
 */
template <std::floating_point T, std::uniform_random_bit_generator Prng>
void flip_sign_bits(std::span<T> v, Prng& prng) noexcept {
    static_assert(Prng::max() == std::numeric_limits<uint64_t>::max());
    const size_t blocks = v.size() / 64;
    const size_t rem = v.size() % 64;
    using UintType = ToUint<T>;

    // Each PRNG invocation yields 64 presumed independent pseudo-random bits.
    // For each distinct bit we XOR it with the MSB (IEEE fp sign bit) of its
    // corresponding input/output span element of a given processing block.
    // This results in a pretty efficient and also fully invertible sign transform.
    auto flip_msb = [](T& elem, const uint64_t rand_bits, const size_t idx) noexcept {
        const UintType as_uint = std::bit_cast<UintType>(elem);
        const UintType rand_msb = ((rand_bits >> idx) & 1) << ((sizeof(UintType) * 8) - 1);
        elem = std::bit_cast<T>(as_uint ^ rand_msb);
    };

    size_t i = 0;
    // We dangle fixed block size processing alluringly in front of the compiler,
    // tempting it to unroll and/or auto-vectorize the inner loop. GCC will happily
    // bite (https://godbolt.org/z/15r655n9P), although the generated code does
    // look a bit on the heavy side...
    for (; i < blocks; ++i) {
        const uint64_t bits = prng();
        for (size_t j = 0; j < 64; ++j) {
            flip_msb(v[i * 64 + j], bits, j);
        }
    }
    if (rem != 0) {
        const uint64_t bits = prng();
        for (size_t j = 0; j < rem; ++j) {
            flip_msb(v[i * 64 + j], bits, j);
        }
    }
}

} // namespace vespalib::quant
