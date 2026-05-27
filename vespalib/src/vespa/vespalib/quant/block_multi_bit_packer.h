// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <bit>
#include <cstddef>
#include <cstdint>

namespace vespalib::quant {

/*
 * Bit count-specialized functions for efficiently packing and unpacking multiple
 * bits in a blockwise manner, with dedicated functions for handling non-block
 * sized remainder cases (somewhat less efficiently).
 */

template <uint8_t B> struct BlockMultiBitPacker;

/*
 * General notes:
 *  - We can't make any assumptions about the initial state of the destination
 *    array elements (both for packing and unpacking), so we must ensure that all
 *    "unrelated" bits are zeroed.
 *  - Related to the above point, we also don't make any assumptions on the
 *    _source_ element bits that are > the packing bit-count, so we must mask
 *    those away.
 *  - (un)pack1_to_7 is only called in a context where the compiler knows (due
 *    to the branches taken and the constraints on the input) that `n` is
 *    guaranteed to be in the range [1, 7]. This allows for opportunistically
 *    unrolling remainder for-loops when inlined.
 */

/*
 * 1-bit packed layout:
 *
 *    byte 0
 * | 7654 3210 |
 *
 */
template <>
struct BlockMultiBitPacker<1> {
    inline static void pack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        *dst = (src[0] & 0x1) << 0;
        *dst |= (src[1] & 0x1) << 1;
        *dst |= (src[2] & 0x1) << 2;
        *dst |= (src[3] & 0x1) << 3;
        *dst |= (src[4] & 0x1) << 4;
        *dst |= (src[5] & 0x1) << 5;
        *dst |= (src[6] & 0x1) << 6;
        *dst |= (src[7] & 0x1) << 7;
    }

    inline static void pack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                  const size_t n) noexcept {
        *dst = (src[0] & 0x1); // Must ensure `dst` is initialized before ORing in anything
        for (size_t i = 1; i < n; ++i) {
            *dst |= (src[i] & 0x1) << i;
        }
    }

    inline static void unpack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        dst[0] = (*src >> 0) & 0x1;
        dst[1] = (*src >> 1) & 0x1;
        dst[2] = (*src >> 2) & 0x1;
        dst[3] = (*src >> 3) & 0x1;
        dst[4] = (*src >> 4) & 0x1;
        dst[5] = (*src >> 5) & 0x1;
        dst[6] = (*src >> 6) & 0x1;
        dst[7] = (*src >> 7) & 0x1;
    }

    inline static void unpack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                    const size_t n) noexcept {
        for (size_t i = 0; i < n; ++i) {
            dst[i] = (*src >> i) & 0x1;
        }
    }
};

/*
 * 2-bit packed layout:
 *
 *    byte 0      byte 1
 * | 3322 1100 | 7766 5544 |
 */
template <>
struct BlockMultiBitPacker<2> {
    inline static void pack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        dst[0] = (src[0] & 0x3) << 0;
        dst[0] |= (src[1] & 0x3) << 2;
        dst[0] |= (src[2] & 0x3) << 4;
        dst[0] |= (src[3] & 0x3) << 6;
        dst[1] = (src[4] & 0x3) << 0;
        dst[1] |= (src[5] & 0x3) << 2;
        dst[1] |= (src[6] & 0x3) << 4;
        dst[1] |= (src[7] & 0x3) << 6;
    }

    inline static void pack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                  const size_t n) noexcept {
        dst[0] = (src[0] & 0x3) << 0;
        if (n == 1) {
            return;
        }
        dst[0] |= (src[1] & 0x3) << 2;
        if (n == 2) {
            return;
        }
        dst[0] |= (src[2] & 0x3) << 4;
        if (n == 3) {
            return;
        }
        dst[0] |= (src[3] & 0x3) << 6;
        if (n == 4) {
            return;
        }
        dst[1] = (src[4] & 0x3) << 0;
        if (n == 5) {
            return;
        }
        dst[1] |= (src[5] & 0x3) << 2;
        if (n == 6) {
            return;
        }
        dst[1] |= (src[6] & 0x3) << 4;
        if (n == 7) {
            return;
        }
        dst[1] |= (src[7] & 0x3) << 6;
    }

    inline static void unpack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        dst[0] = (src[0] >> 0) & 0x3;
        dst[1] = (src[0] >> 2) & 0x3;
        dst[2] = (src[0] >> 4) & 0x3;
        dst[3] = (src[0] >> 6) & 0x3;
        dst[4] = (src[1] >> 0) & 0x3;
        dst[5] = (src[1] >> 2) & 0x3;
        dst[6] = (src[1] >> 4) & 0x3;
        dst[7] = (src[1] >> 6) & 0x3;
    }

    inline static void unpack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                    const size_t n) noexcept {
        dst[0] = (src[0] >> 0) & 0x3;
        if (n == 1) {
            return;
        }
        dst[1] = (src[0] >> 2) & 0x3;
        if (n == 2) {
            return;
        }
        dst[2] = (src[0] >> 4) & 0x3;
        if (n == 3) {
            return;
        }
        dst[3] = (src[0] >> 6) & 0x3;
        if (n == 4) {
            return;
        }
        dst[4] = (src[1] >> 0) & 0x3;
        if (n == 5) {
            return;
        }
        dst[5] = (src[1] >> 2) & 0x3;
        if (n == 6) {
            return;
        }
        dst[6] = (src[1] >> 4) & 0x3;
        if (n == 7) {
            return;
        }
        dst[7] = (src[1] >> 6) & 0x3;
    }
};

/*
 * 3-bit packed layout:
 *
 *    byte 0      byte 1      byte 2
 * | 2211 1000 | 5444 3332 | 7776 6655 |
 */
template <>
struct BlockMultiBitPacker<3> {
    static_assert(std::endian::native == std::endian::little);

    inline static void pack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        uint32_t tmp = 0;
        tmp |= (src[0] & 0x7) << (3 * 0);
        tmp |= (src[1] & 0x7) << (3 * 1);
        tmp |= (src[2] & 0x7) << (3 * 2);
        tmp |= (src[3] & 0x7) << (3 * 3);
        tmp |= (src[4] & 0x7) << (3 * 4);
        tmp |= (src[5] & 0x7) << (3 * 5);
        tmp |= (src[6] & 0x7) << (3 * 6);
        tmp |= (src[7] & 0x7) << (3 * 7);
        memcpy(dst, &tmp, 3); // Assumes little-endian
    }

    inline static void pack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                  const size_t n) noexcept {
        uint32_t tmp = 0;
        for (size_t i = 0; i < n; ++i) {
            tmp |= (src[i] & 0x7) << (3 * i);
        }
        dst[0] = tmp & 0xff;
        if (n < 3) {
            return; // 6 bits packed, 2 remainder zero bits
        }
        dst[1] = (tmp >> 8) & 0xff;
        if (n < 6) {
            return; // 8+5 bits packed, 1 remainder zero bit
        }
        dst[2] = tmp >> 16;
    }

    inline static void unpack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        const uint32_t tmp = src[0] | (src[1] << 8) | (src[2] << 16);
        dst[0] = (tmp >> (3 * 0)) & 0x7;
        dst[1] = (tmp >> (3 * 1)) & 0x7;
        dst[2] = (tmp >> (3 * 2)) & 0x7;
        dst[3] = (tmp >> (3 * 3)) & 0x7;
        dst[4] = (tmp >> (3 * 4)) & 0x7;
        dst[5] = (tmp >> (3 * 5)) & 0x7;
        dst[6] = (tmp >> (3 * 6)) & 0x7;
        dst[7] = (tmp >> (3 * 7)) & 0x7;
    }

    inline static void unpack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                    const size_t n) noexcept {
        uint32_t tmp = src[0];
        if (n > 2) {
            tmp |= src[1] << 8;
        }
        if (n > 5) {
            tmp |= src[2] << 16;
        }
        for (size_t i = 0; i < n; ++i) {
            dst[i] = (tmp >> (3 * i)) & 0x7;
        }
    }
};

/*
 * 4-bit packed layout:
 *
 *    byte 0      byte 1      byte 2      byte 3
 * | 1111 0000 | 3333 2222 | 5555 4444 | 7777 6666 |
 */
template <>
struct BlockMultiBitPacker<4> {
    inline static void pack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        dst[0] = (src[0] & 0xf);
        dst[0] |= (src[1] & 0xf) << 4;
        dst[1] = (src[2] & 0xf);
        dst[1] |= (src[3] & 0xf) << 4;
        dst[2] = (src[4] & 0xf);
        dst[2] |= (src[5] & 0xf) << 4;
        dst[3] = (src[6] & 0xf);
        dst[3] |= (src[7] & 0xf) << 4;
    }

    inline static void pack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                  const size_t n) noexcept {
        dst[0] = (src[0] & 0xf) << 0;
        if (n == 1) {
            return;
        }
        dst[0] |= (src[1] & 0xf) << 4;
        if (n == 2) {
            return;
        }
        dst[1] = (src[2] & 0xf) << 0;
        if (n == 3) {
            return;
        }
        dst[1] |= (src[3] & 0xf) << 4;
        if (n == 4) {
            return;
        }
        dst[2] = (src[4] & 0xf) << 0;
        if (n == 5) {
            return;
        }
        dst[2] |= (src[5] & 0xf) << 4;
        if (n == 6) {
            return;
        }
        dst[3] = (src[6] & 0xf) << 0;
        if (n == 7) {
            return;
        }
        dst[3] |= (src[7] & 0xf) << 4;
    }

    inline static void unpack8(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src) noexcept {
        dst[0] = src[0] & 0xf;
        dst[1] = src[0] >> 4;
        dst[2] = src[1] & 0xf;
        dst[3] = src[1] >> 4;
        dst[4] = src[2] & 0xf;
        dst[5] = src[2] >> 4;
        dst[6] = src[3] & 0xf;
        dst[7] = src[3] >> 4;
    }

    // TODO reverse order switch-case fallthrough (quasi Duff's device)?
    inline static void unpack1_to_7(uint8_t* __restrict__ dst, const uint8_t* __restrict__ src,
                                    const size_t n) noexcept {
        dst[0] = src[0] & 0xf;
        if (n == 1) {
            return;
        }
        dst[1] = src[0] >> 4;
        if (n == 2) {
            return;
        }
        dst[2] = src[1] & 0xf;
        if (n == 3) {
            return;
        }
        dst[3] = src[1] >> 4;
        if (n == 4) {
            return;
        }
        dst[4] = src[2] & 0xf;
        if (n == 5) {
            return;
        }
        dst[5] = src[2] >> 4;
        if (n == 6) {
            return;
        }
        dst[6] = src[3] & 0xf;
        if (n == 7) {
            return;
        }
        dst[7] = src[3] >> 4;
    }
};

} // namespace vespalib::quant
