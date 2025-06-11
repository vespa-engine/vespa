// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>

namespace vespalib {

// Note: all these over/underflow checks require the expected result
// type to be explicitly provided. This is because implicit integer type
// promotions can have unexpected semantics (e.g. u8 + u8 will be promoted
// via `int` and thus _not_ be considered an overflow case).

// Well-defined overflow checking for addition of two integers
template <std::integral R, std::integral T0, std::integral T1>
[[nodiscard]] inline constexpr bool
add_would_overflow(T0 lhs, T1 rhs) noexcept {
    return __builtin_add_overflow_p(lhs, rhs, R{});
}

// Well-defined underflow checking for subtraction of two integers
template <std::integral R, std::integral T0, std::integral T1>
[[nodiscard]] inline constexpr bool
sub_would_underflow(T0 lhs, T1 rhs) noexcept {
    // The intrinsic calls this overflow, but we'll refer to it as underflow
    return __builtin_sub_overflow_p(lhs, rhs, R{});
}

// Well-defined overflow checking for multiplication of two integers
template <std::integral R, std::integral T0, std::integral T1>
[[nodiscard]] inline constexpr bool
mul_would_overflow(T0 lhs, T1 rhs) noexcept {
    return __builtin_mul_overflow_p(lhs, rhs, R{});
}

}
