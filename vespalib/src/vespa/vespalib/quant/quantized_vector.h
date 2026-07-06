// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <span>
#include <type_traits>

namespace vespalib::quant {

/*
 * Strong view type over a bit-packed sequence of centroid indexes, as produced by
 * MultiBitPacker. Distinguishes "the packed bits sub-region" of a quantized vector
 * from an arbitrary byte buffer at the type level, so the two can't be crossed by
 * accident. Templated on the (possibly const-qualified) byte type; use the
 * `PackedBits`/`MutablePackedBits` aliases rather than naming this directly.
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

/*
 * Strong view type over a full quantized vector buffer, which uses the layout
 * `[f32 scale factor][packed centroid-index bits]`. Encapsulates all knowledge of
 * this layout (scale factor extraction and the packed-bits sub-region offset), so
 * callers never do the byte arithmetic themselves and can't confuse a quantized
 * vector with a raw buffer or with the packed bits it contains.
 *
 * This is a non-owning view; the underlying buffer must outlive the view. Templated
 * on the (possibly const-qualified) byte type; use the `QuantizedVector`/
 * `MutableQuantizedVector` aliases rather than naming this directly.
 */
template <typename Byte>
class basic_quantized_vector {
    std::span<Byte>         _buf;
    static constexpr size_t scale_bytes = sizeof(float); // TODO configurable scale factor precision (f16 vs f32)?
public:
    explicit constexpr basic_quantized_vector(std::span<Byte> buf) noexcept : _buf(buf) {}

    [[nodiscard]] constexpr std::span<Byte> raw() const noexcept { return _buf; }
    [[nodiscard]] constexpr Byte* data() const noexcept { return _buf.data(); }
    [[nodiscard]] constexpr size_t size() const noexcept { return _buf.size(); }

    // Extracts the f32 scale factor stored at the head of the buffer.
    [[nodiscard]] float scale() const noexcept {
        float s;
        memcpy(&s, _buf.data(), scale_bytes);
        return s;
    }
    // Writes the f32 scale factor to the head of the buffer. Only available on mutable views.
    void set_scale(float s) const noexcept
        requires(!std::is_const_v<Byte>)
    {
        memcpy(_buf.data(), &s, scale_bytes);
    }
    // The sub-view holding just the bit-packed centroid indexes (i.e. everything past the scale factor).
    [[nodiscard]] constexpr basic_packed_bits<Byte> packed_bits() const noexcept {
        return basic_packed_bits<Byte>(_buf.subspan(scale_bytes));
    }
};
using QuantizedVector = basic_quantized_vector<const uint8_t>;
using MutableQuantizedVector = basic_quantized_vector<uint8_t>;

} // namespace vespalib::quant
