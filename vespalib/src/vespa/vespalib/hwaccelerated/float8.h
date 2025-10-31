// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fp8_luts.h"
#include "fn_table.h"

namespace vespalib::hwaccelerated {

struct Float8E4M3FN {
    using TagType = dispatch::Fp8E4M3FNTag;

    uint8_t _bits;
    constexpr Float8E4M3FN() noexcept : _bits(0) {}
    constexpr explicit Float8E4M3FN(uint8_t v) noexcept : _bits(v) {}
    float to_float() const noexcept {
        return std::bit_cast<float>(fp8_e4m3fn_f32_bits_lut[_bits]);
    }
    static bool is_finite(uint8_t v) noexcept {
        // NaN has all non-sign bits set. FN; no infinity
        return (v & 0b0111'1111) != 0b0111'1111;
    }
};

struct Float8E5M2 {
    using TagType = dispatch::Fp8E5M3Tag;

    uint8_t _bits;
    constexpr Float8E5M2() noexcept : _bits(0) {}
    constexpr explicit Float8E5M2(uint8_t v) noexcept : _bits(v) {}
    float to_float() const noexcept {
        return std::bit_cast<float>(fp8_e5m2_f32_bits_lut[_bits]);
    }
    static bool is_finite(uint8_t v) noexcept {
        // NaN/Inf have all 5 exponent bits set
        return (v & 0b0111'1100) != 0b0111'1100;
    }
};

} // vespalib::hwaccelerated
