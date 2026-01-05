// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "float8_luts.h"
#include "microfloat.h"
#include <bit>

namespace vespalib::hwaccelerated {

struct Float8E4M3FN {
    using TagType = dispatch::Fp8E4M3FNTag;

    using native_type = uint8_t;

    native_type _bits;
    constexpr Float8E4M3FN() noexcept : _bits(0) {}
    constexpr explicit Float8E4M3FN(native_type v) noexcept : _bits(v) {}

    [[nodiscard]] float to_float() const noexcept {
        return std::bit_cast<float>(fp8_e4m3fn_f32_bits_lut[_bits]);
    }
    [[nodiscard]] static constexpr bool is_finite(native_type v) noexcept {
        // NaN has all non-sign bits set. FN; no infinity
        return (v & 0b0111'1111) != 0b0111'1111;
    }
    [[nodiscard]] static constexpr MicroFloatKind kind() noexcept { return MicroFloatKind::FP8_E4M2FN; }
};

struct Float8E5M2 {
    using TagType = dispatch::Fp8E5M3Tag;

    using native_type = uint8_t;

    native_type _bits;
    constexpr Float8E5M2() noexcept : _bits(0) {}
    constexpr explicit Float8E5M2(native_type v) noexcept : _bits(v) {}

    [[nodiscard]] float to_float() const noexcept {
        return std::bit_cast<float>(fp8_e5m2_f32_bits_lut[_bits]);
    }
    [[nodiscard]] static constexpr bool is_finite(native_type v) noexcept {
        // NaN/Inf have all 5 exponent bits set
        return (v & 0b0111'1100) != 0b0111'1100;
    }
    [[nodiscard]] static constexpr MicroFloatKind kind() noexcept { return MicroFloatKind::FP8_E5M2; }
};

} // vespalib::hwaccelerated
