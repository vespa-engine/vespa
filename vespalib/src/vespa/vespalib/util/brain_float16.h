// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <bit>
#include <cstdint>
#include <cstring>

namespace vespalib {

class BrainFloat16 {
private:
    uint16_t _bits;
    struct TwoU16 {
        uint16_t u1;
        uint16_t u2;
    };
public:
    constexpr BrainFloat16(float value) noexcept : _bits(float_to_bits(value)) {}
    BrainFloat16() noexcept = default;
    ~BrainFloat16() noexcept = default;
    constexpr BrainFloat16(const BrainFloat16 &other) noexcept = default;
    constexpr BrainFloat16(BrainFloat16 &&other) noexcept = default;
    constexpr BrainFloat16& operator=(const BrainFloat16 &other) noexcept = default;
    constexpr BrainFloat16& operator=(BrainFloat16 &&other) noexcept = default;

    constexpr operator float () const noexcept { return bits_to_float(_bits); }

    constexpr float to_float() const noexcept { return bits_to_float(_bits); }

    constexpr void assign(float value) noexcept { _bits = float_to_bits(value); }

    static constexpr uint16_t float_to_bits(float value) noexcept {
        TwoU16 both{0,0};
        static_assert(sizeof(TwoU16) == sizeof(float));
        memcpy(&both, &value, sizeof(float));
        if constexpr (std::endian::native == std::endian::big) {
            return both.u1;
        } else if constexpr (std::endian::native == std::endian::little) {
            return both.u2;
        } else {
            return 0;
        }
    }

    static constexpr float bits_to_float(uint16_t bits) noexcept {
        TwoU16 both{0,0};
        if constexpr (std::endian::native == std::endian::big) {
            both.u1 = bits;
        } else if constexpr (std::endian::native == std::endian::little) {
            both.u2 = bits;
        } else {
            return 0.0;
        }
        float result = 0.0;
        static_assert(sizeof(TwoU16) == sizeof(float));
        memcpy(&result, &both, sizeof(float));
        return result;
    }
};

}

namespace std {
template<> class numeric_limits<vespalib::BrainFloat16> {
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

    static constexpr vespalib::BrainFloat16 denorm_min() noexcept { return 0x1.0p-133; }
    static constexpr vespalib::BrainFloat16 epsilon() noexcept { return 0x1.0p-7; }
    static constexpr vespalib::BrainFloat16 lowest() noexcept { return -0x1.FEp127; }
    static constexpr vespalib::BrainFloat16 max() noexcept { return 0x1.FEp127; }
    static constexpr vespalib::BrainFloat16 min() noexcept { return 0x1.0p-126; }
    static constexpr vespalib::BrainFloat16 round_error() noexcept { return 1.0; }
    static constexpr vespalib::BrainFloat16 infinity() noexcept {
        return std::numeric_limits<float>::infinity();
    }
    static constexpr vespalib::BrainFloat16 quiet_NaN() noexcept {
        return std::numeric_limits<float>::quiet_NaN();
    }
    static constexpr vespalib::BrainFloat16 signaling_NaN() noexcept {
        return std::numeric_limits<float>::signaling_NaN();
    }
};

}
