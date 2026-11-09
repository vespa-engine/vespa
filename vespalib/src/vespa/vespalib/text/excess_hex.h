// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <bit>
#include <charconv>
#include <concepts>
#include <cstdint>
#include <optional>
#include <string_view>
#include <type_traits>

namespace vespalib::excess_hex {

namespace detail {

template <typename T> struct bits_for;
template <> struct bits_for<int32_t> { using type = uint32_t; };
template <> struct bits_for<int64_t> { using type = uint64_t; };
template <> struct bits_for<float>   { using type = uint32_t; };
template <> struct bits_for<double>  { using type = uint64_t; };

/**
 * Parses exactly sizeof(U) * 2 hex digits.
 */
template <std::unsigned_integral U> std::optional<U> parse_hex(std::string_view hex) {
    if (hex.size() != sizeof(U) * 2) {
        return std::nullopt;
    }
    U bits = 0;
    auto [ptr, ec] = std::from_chars(hex.data(), hex.data() + hex.size(), bits, 16);
    if (ec != std::errc() || ptr != hex.data() + hex.size()) {
        return std::nullopt;
    }
    return bits;
}

} // namespace detail

/**
 * Decodes a value from the order-preserving "excess hex" encoding produced
 * by com.yahoo.text.Text in Java (toExcessHex8 / toExcessHex16 for int and
 * long, floatToExcessHex8 / doubleToExcessHex16 for float and double).
 *
 * The input must be exactly sizeof(T) * 2 hex digits (either case).
 *
 * Integers are biased by flipping the sign bit. For floating point values,
 * positive values have the sign bit set, while negative values have all
 * bits inverted.
 *
 * Returns std::nullopt if the input is malformed.
 */
template <typename T>
    requires std::same_as<T, int32_t> || std::same_as<T, int64_t> || std::same_as<T, float> ||
             std::same_as<T, double>
std::optional<T> decode(std::string_view hex) {
    using U = typename detail::bits_for<T>::type;
    auto parsed = detail::parse_hex<U>(hex);
    if (!parsed) {
        return std::nullopt;
    }
    constexpr U sign_bit = U(1) << (sizeof(U) * 8 - 1);
    U bits = *parsed;
    if constexpr (std::is_integral_v<T>) {
        bits ^= sign_bit;
    } else {
        bits = (bits & sign_bit) ? U(bits ^ sign_bit) : U(~bits);
    }
    return std::bit_cast<T>(bits);
}

} // namespace vespalib::excess_hex
