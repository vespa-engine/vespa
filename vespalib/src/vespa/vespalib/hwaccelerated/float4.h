// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <concepts>

namespace vespalib::hwaccelerated {

/**
 * Utility for converting the bit representation of an FP4 E2M1 floating point
 * losslessly to that of a wider floating point format. Callers can bit_cast
 * the widened bit representation to the appropriate floating point type.
 *
 * E2M1 is a very simple format with (as the name implies) only 2 exponent bits
 * and a single mantissa bit (and also an implicit single sign bit). There are
 * no NaN values and no Infinity. Its entire dynamic range is contained within
 * [-6, 6] and it has a single subnormal at +/- 0.5. We always map this subnormal
 * to an exponent of 2^-1, which means the widened type needs to have ExpBits
 * and ExpBias that can represent this exactly.
 */
template <std::unsigned_integral T, size_t ExpBits, size_t ExpBias>
struct Float4E2M1Conv {
    constexpr static size_t fp4_bits = 4;
    constexpr static size_t fp4_exp_bit_count = 2;
    constexpr static size_t fp4_exp_bias = 1;
    constexpr static size_t fp4_mantissa_bit_count = 1;

    constexpr static size_t wide_bits = sizeof(T) * 8;
    constexpr static size_t wide_exp_bit_count = ExpBits;
    constexpr static size_t wide_exp_bias = ExpBias;
    constexpr static size_t wide_mantissa_bit_count = wide_bits - 1 /*sign*/ - wide_exp_bit_count;

    // Need bigger exponent since we'll map our single subnormal (+/- 0.5)
    // to a 2^-1 target exponent (i.e. 0.5) with a zero mantissa.
    static_assert(wide_exp_bit_count > fp4_exp_bit_count);
    static_assert(wide_exp_bias > fp4_exp_bias);
    static_assert(wide_mantissa_bit_count >= fp4_mantissa_bit_count);

    constexpr static T widen(uint8_t v) noexcept {
        v &= 0x0f;
        const T my_sign = v >> 3;
        const T my_exp  = (v >> 1) & 0x3;
        const T my_mant = v & 1;
        // The sign bit can always be copied verbatim, but must adjust exponent and mantissa.
        T adj_exp = 0;
        T adj_mantissa = 0;

        if (my_exp == 0 && my_mant == 1) { // subnormal?
            adj_exp = wide_exp_bias - 1; // exp == bias is 2^0, so this leaves us with 2^-1
            // Mantissa remains zero
        } else if (my_exp != 0) { // normalized number (no NaN or Inf to worry about)
            adj_exp = wide_exp_bias - fp4_exp_bias + my_exp;
            adj_mantissa = (my_mant << (wide_mantissa_bit_count - fp4_mantissa_bit_count));
        } // else: +/- zero
        return ((my_sign << (wide_bits - 1))
                | (adj_exp << wide_mantissa_bit_count)
                | adj_mantissa);
    }
};

// These type names just roll off the tongue
using Float4E2M1ToFloat8E5M2Conv   = Float4E2M1Conv<uint8_t,  5, 15>;
using Float4E2M1ToFloat8E4M3FnConv = Float4E2M1Conv<uint8_t,  4, 7>;
using Float4E2M1ToFloat32Conv      = Float4E2M1Conv<uint32_t, 8, 127>;

} // vespalib::hwaccelerated
