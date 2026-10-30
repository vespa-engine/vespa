// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
#include <cstddef>

namespace vespalib::hwaccelerated {

template <typename T>
concept InvocationMeta = requires(T a) {
    { a.is_partial() } -> std::same_as<bool>;
    // If is_partial() == true, returns the number of valid elements, otherwise 0.
    { a.remaining() } -> std::same_as<size_t>;
    { a.unroll_index() } -> std::same_as<size_t>;
};

// A kernel invocation where all input vector lanes are valid.
template <size_t UnrollIdx>
struct BlockInvocation {
    [[nodiscard]] constexpr bool is_partial() const noexcept { return false; }
    [[nodiscard]] constexpr size_t remaining() const noexcept { return 0; }
    [[nodiscard]] constexpr size_t unroll_index() const noexcept { return UnrollIdx; }
};

// A kernel invocation where only the first `remaining()` lanes of the input
// vector are valid. Any lanes beyond this will always be zero-valued, but
// kernels must take care to do appropriate masking if this is not sufficient
// to ensure correctness.
struct PartialInvocation {
    size_t rem;
    [[nodiscard]] constexpr PartialInvocation(size_t rem_) noexcept : rem(rem_) {}
    [[nodiscard]] constexpr bool is_partial() const noexcept { return true; }
    [[nodiscard]] constexpr size_t remaining() const noexcept { return rem; }
    [[nodiscard]] constexpr size_t unroll_index() const noexcept { return 0; }
};

} // namespace vespalib::hwaccelerated
