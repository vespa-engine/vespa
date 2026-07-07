// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <span>

namespace vespalib::quant {

/*
 * Strong view type over a bit-packed sequence of u8 values, as produced by
 * MultiBitPacker. Distinguishes "a bit-packed buffer" from an arbitrary byte buffer at
 * the type level, so the two can't be crossed by accident. Templated on the (possibly
 * const-qualified) byte type; use the `PackedBits`/`MutablePackedBits` aliases rather
 * than naming this directly.
 */
template <typename Byte>
class basic_packed_bits {
    std::span<Byte> _bits;

public:
    explicit constexpr basic_packed_bits(std::span<Byte> bits) noexcept : _bits(bits) {}
    [[nodiscard]] constexpr Byte* data() const noexcept { return _bits.data(); }
    [[nodiscard]] constexpr size_t size() const noexcept { return _bits.size(); }
    [[nodiscard]] constexpr std::span<Byte> span() const noexcept { return _bits; }
};
using PackedBits = basic_packed_bits<const uint8_t>;
using MutablePackedBits = basic_packed_bits<uint8_t>;

} // namespace vespalib::quant
