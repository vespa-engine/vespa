// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <bit>
#include <cstdint>
#include <cstring>
#include <limits>

namespace vespalib {

/**
 * Class holding 16-bit floating-point numbers.
 * Truncated version of normal 32-bit float; the sign and
 * exponent are kept as-is but the mantissa has only 8-bit
 * precision.  Well suited for ML / AI, halving memory
 * requirements for large vectors and similar data.
 * Direct HW support possible (AVX-512 BF16 extension etc.)
 * See also:
 * https://en.wikipedia.org/wiki/Bfloat16_floating-point_format
 **/
class BFloat16 {
private:
    uint16_t _bits;
    struct TwoU16 {
        uint16_t u1;
        uint16_t u2;
    };

    template<std::endian native_endian = std::endian::native>
    static constexpr uint16_t float_to_bits(float value) noexcept {
        TwoU16 both{0,0};
        static_assert(sizeof(TwoU16) == sizeof(float));
        memcpy(&both, &value, sizeof(float));
        if constexpr (native_endian == std::endian::big) {
            return both.u1;
        } else {
            static_assert(native_endian == std::endian::little,
                          "Unknown endian, cannot handle");
            return both.u2;
        }
    }

    template<std::endian native_endian = std::endian::native>
    static constexpr float bits_to_float(uint16_t bits) noexcept {
        TwoU16 both{0,0};
        if constexpr (native_endian == std::endian::big) {
            both.u1 = bits;
        } else {
            static_assert(native_endian == std::endian::little,
                          "Unknown endian, cannot handle");
            both.u2 = bits;
        }
        float result = 0.0;
        static_assert(sizeof(TwoU16) == sizeof(float));
        memcpy(&result, &both, sizeof(float));
        return result;
    }
public:
    constexpr BFloat16(float value) noexcept : _bits(float_to_bits(value)) {}
    BFloat16() noexcept = default;
    ~BFloat16() noexcept = default;
    constexpr BFloat16(const BFloat16 &other) noexcept = default;
    constexpr BFloat16(BFloat16 &&other) noexcept = default;
    constexpr BFloat16& operator=(const BFloat16 &other) noexcept = default;
    constexpr BFloat16& operator=(BFloat16 &&other) noexcept = default;
    constexpr BFloat16& operator=(float value) noexcept {
        _bits = float_to_bits(value);
        return *this;
    }

    constexpr operator float () const noexcept { return bits_to_float(_bits); }

    constexpr float to_float() const noexcept { return bits_to_float(_bits); }
    constexpr void assign(float value) noexcept { _bits = float_to_bits(value); }

    constexpr uint16_t get_bits() const { return _bits; }
    constexpr void assign_bits(uint16_t value) noexcept { _bits = value; }
};

}

namespace std {
template<> class numeric_limits<vespalib::BFloat16> {
public:
    static constexpr bool is_specialized = true;
    static constexpr bool is_signed = true;
    static constexpr bool is_integer = false;
    static constexpr bool is_exact = false;
    static constexpr bool has_infinity = false;
    static constexpr bool has_quiet_NaN = true;
    static constexpr bool has_signaling_NaN = true;
    static constexpr bool has_denorm = true;
    static constexpr bool has_denorm_loss = false;
    static constexpr bool is_iec559 = false;
    static constexpr bool is_bounded = true;
    static constexpr bool is_modulo = false;
    static constexpr bool traps = false;
    static constexpr bool tinyness_before = false;

    static constexpr std::float_round_style round_style = std::round_toward_zero;
    static constexpr int radix = 2;

    static constexpr int digits = 8;
    static constexpr int digits10 = 2;
    static constexpr int max_digits10 = 4;

    static constexpr int min_exponent = -125;
    static constexpr int min_exponent10 = -2;

    static constexpr int max_exponent = 128;
    static constexpr int max_exponent10 = 38;

    static constexpr vespalib::BFloat16 denorm_min() noexcept { return 0x1.0p-133; }
    static constexpr vespalib::BFloat16 epsilon() noexcept { return 0x1.0p-7; }
    static constexpr vespalib::BFloat16 lowest() noexcept { return -0x1.FEp127; }
    static constexpr vespalib::BFloat16 max() noexcept { return 0x1.FEp127; }
    static constexpr vespalib::BFloat16 min() noexcept { return 0x1.0p-126; }
    static constexpr vespalib::BFloat16 round_error() noexcept { return 1.0; }
    static constexpr vespalib::BFloat16 infinity() noexcept {
        return std::numeric_limits<float>::infinity();
    }
    static constexpr vespalib::BFloat16 quiet_NaN() noexcept {
        return std::numeric_limits<float>::quiet_NaN();
    }
    static constexpr vespalib::BFloat16 signaling_NaN() noexcept {
        return std::numeric_limits<float>::signaling_NaN();
    }
};

}
