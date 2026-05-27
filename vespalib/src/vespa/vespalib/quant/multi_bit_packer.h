// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "block_multi_bit_packer.h"

#include <cstddef>
#include <cstdint>
#include <cstdlib>

namespace vespalib::quant {

template <uint8_t B> struct BlockMultiBitPacker;

/*
 * Utility for packing and unpacking u8 values to/from `Bits` number of bits per value.
 *
 * The packing layout used is very simple; the packed buffer is treated as a large
 * contiguous sequence of bits packed into bytes LSB-first, with zero-bit MSB padding
 * of remainders in the last byte.
 *
 * Example: a sequence of 4-bit values [ A, B, C, D, E] is packed as follows:
 *
 * byte:         0           1           2
 *         | BBBB AAAA | DDDD CCCC | 0000 EEEE |
 * bit:      7       0   7       0   7       0
 */
template <uint8_t Bits>
struct MultiBitPacker {
    static_assert(Bits >= 1 && Bits <= 4, "bit count outside valid range [1, 4]");

    /*
     * Pack `n` u8 values from `src` into `packed_bytes(n)` bytes in `dst`, representing
     * each value using exactly `Bits` number of bits. Source values that are greater than
     * or equal to 2**Bits are _truncated_; there is no implicit _saturation_ of values.
     *
     * `dst` does not have to be zeroed out prior to calling this function.
     *
     * If the number of written elements*Bits does not evenly divide into bytes, the last
     * byte will be padded with 0-bits.
     *
     * Preconditions:
     *   - `dst` and `src` arrays must not alias.
     */
    static void pack(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src, const size_t n) noexcept {
        // We process elements in blocks of 8 since this allows us to write (for
        // packing) or read (for unpacking) 1, 2, 3 or 4 bytes at a time for
        // 1, 2, 3 and 4 bits, respectively. So the number of bits conveniently
        // happens to be == the number of bytes.
        constexpr size_t out_bytes_per_block = Bits;
        const size_t     blocks = n / 8;
        const size_t     rem = n % 8;

        for (size_t b = 0; b < blocks; ++b) {
            BlockMultiBitPacker<Bits>::pack8(dst, src);
            src += 8;
            dst += out_bytes_per_block;
        }
        if (rem != 0) {
            BlockMultiBitPacker<Bits>::pack1_to_7(dst, src, rem);
        }
    }

    /*
     * Unpack `n` u8 values from `packed_bytes(n)` bytes in `src` into `dst`. I.e. each
     * packed value is unpacked and written as its own u8. Exactly `n` bytes are written
     * to `dst`.
     *
     * Preconditions:
     *   - `dst` and `src` arrays must not alias.
     */
    static void unpack(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src, const size_t n) noexcept {
        constexpr size_t in_bytes_per_block = Bits;
        const size_t     blocks = n / 8;
        const size_t     rem = n % 8;

        for (size_t b = 0; b < blocks; ++b) {
            BlockMultiBitPacker<Bits>::unpack8(dst, src);
            src += in_bytes_per_block;
            dst += 8;
        }
        if (rem != 0) {
            BlockMultiBitPacker<Bits>::unpack1_to_7(dst, src, rem);
        }
    }

    /*
     * Computes the number of bytes that `pack(dst, src, n)` will write into `dst` when
     * packing `n` elements from `src`.
     */
    [[nodiscard]] static constexpr size_t packed_bytes(const size_t n) noexcept {
        return ((n * Bits) / 8) + (((n * Bits) % 8) != 0 ? 1 : 0);
    }
};

/*
 * Compile-time instantiates `fn` for all possible BitPacker bit-specializations
 * and invokes the one matching `bits` at runtime as `fn(BitPacker<bits> bp)`.
 *
 * Use `declspec(bp)` to get access to the static functions on the passed BitPacker.
 *
 * Precondition: `bits` is in the range [1, 4].
 */
template <typename Fn>
auto with_packer_for_bit_count(uint8_t bits, Fn fn) {
    switch (bits) {
    case 1:
        return fn(MultiBitPacker<1>());
    case 2:
        return fn(MultiBitPacker<2>());
    case 3:
        return fn(MultiBitPacker<3>());
    case 4:
        return fn(MultiBitPacker<4>());
    default:
        abort();
    }
}

} // namespace vespalib::quant
