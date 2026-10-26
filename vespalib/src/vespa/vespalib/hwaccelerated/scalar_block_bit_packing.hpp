// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib::hwaccelerated {

/*
 * Scalar implementations of "vertical" fixed block-based bit packing and "horizontal"
 * remainder bit packing.
 *
 * "Vertical" packing is optimized for SIMD-style packing and unpacking, and ensures
 * the layout of packed bits map 1-1 with the elementwise position of the integers to
 * pack/unpack (requiring only shifting and masking). I.e. the bits "travel vertically"
 * across source/target arrays. To ensure that the positions line up correctly, vertical
 * packing requires a predefined _block size_ that must always be the same for packing
 * and unpacking a given part of the input.
 *
 * "Horizontal" packing has the usual "left to right" layout, where we start in the LSB
 * of byte 0, pack towards the MSB and carry any remainder bits over to the LSB of byte
 * 1 (and so on). Very cozy and easy to understand, and does not require padding to a
 * block size, but is hard to SIMD-optimize since there is no direct mapping from input
 * to output positions when packing/unpacking. I.e. the output may be in a SIMD lane far
 * away from its input.
 *
 * All implementations have the following preconditions:
 *
 * 1. When packing, `raw` must NOT have bits set outside the allowed range!
 *
 * Since we assume all irrelevant bits are 0, we can use XOR instead of OR, which is
 * faster on some platforms. E.g. AArch64 has a 3-operand XOR instruction when FEAT_SHA3
 * is present.
 *
 * 2. The buffers for `raw` and `packed` must not alias!
 *
 * Since we know there's no aliasing, use `__restrict__` to prevent the compiler from
 * generating a bunch of unnecessary code to deal with such an edge case.
 */

/*
 * Vertical bit (un-)packing.
 *
 * These are scalar implementations of the Highway bit packing algorithms, and are intended
 * as a more understandable (hopefully?) reference code and an implementation sanity-check.
 * This kind of code (constexpr iteration count, deterministic and simple access patterns,
 * bitwise ops) is practically catnip for auto-vectorizers, so we'd actually expect this
 * code to be very close to the performance as the _explicitly_ vectorized version for the
 * purpose of packing/unpacking to/from memory buffers directly.
 *
 * The BlockSize argument to pack()/unpack() refers to the size in bytes of the _packed_
 * block. This always maps to 8x the number of _raw_ u8 integers (both for input and output),
 * so the buffers must be sized appropriately. I.e. a 16 byte block (un-)packs 128 u8
 * integers at a time, an 8-byte block (un-)packs 64 u8 integers and so on.
 *
 * The packing algorithm is pretty straight forward. Assuming Bits == 1, we logically
 * split the input into 8 distinct subsequences, each of BlockSize bytes. Each _input_
 * byte at position N in [0, BlockSize) from every subsequence contributes a single bit
 * to the corresponding _output_ byte at position N. This logically happens in parallel
 * per element and can therefore be SIMD-ified easily.
 *
 * Unpacking is the exact reverse of the packing algorithm, so understanding one leads
 * to understanding the other.
 *
 * The concept is the same for other bit widths, but we pack to/unpack from _multiple_
 * bit blocks. The number of such blocks is always equal to Bits. The Bits == 3 case is
 * the most awkward once since 3 bits does not evenly distribute into a single output
 * block and we must therefore smear some position bits _across_ the blocks.
 */
template <uint8_t Bits>
struct ScalarVerticalBlockBitPacker;

// 1-bit pack/unpack
template <>
struct ScalarVerticalBlockBitPacker<1> {
    template <size_t BlockSize>
    __attribute__((always_inline)) static void pack(const uint8_t* __restrict__ raw,
                                                    uint8_t* __restrict__ packed) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   raw0 = raw + 0 * NB;
        const uint8_t*   raw1 = raw + 1 * NB;
        const uint8_t*   raw2 = raw + 2 * NB;
        const uint8_t*   raw3 = raw + 3 * NB;
        const uint8_t*   raw4 = raw + 4 * NB;
        const uint8_t*   raw5 = raw + 5 * NB;
        const uint8_t*   raw6 = raw + 6 * NB;
        const uint8_t*   raw7 = raw + 7 * NB;
        for (size_t i = 0; i < NB; ++i) {
            packed[i] = raw0[i] ^ (raw1[i] << 1) ^ (raw2[i] << 2) ^ (raw3[i] << 3) ^ (raw4[i] << 4) ^ (raw5[i] << 5) ^
                        (raw6[i] << 6) ^ (raw7[i] << 7);
        }
    }

    template <size_t BlockSize>
    __attribute__((always_inline)) static void unpack(const uint8_t* __restrict__ packed,
                                                      uint8_t* __restrict__ raw) noexcept {
        constexpr size_t NB = BlockSize;
        for (size_t i = 0; i < NB; ++i) {
            raw[i + 0 * NB] = (packed[i] >> 0) & 0x1;
            raw[i + 1 * NB] = (packed[i] >> 1) & 0x1;
            raw[i + 2 * NB] = (packed[i] >> 2) & 0x1;
            raw[i + 3 * NB] = (packed[i] >> 3) & 0x1;
            raw[i + 4 * NB] = (packed[i] >> 4) & 0x1;
            raw[i + 5 * NB] = (packed[i] >> 5) & 0x1;
            raw[i + 6 * NB] = (packed[i] >> 6) & 0x1;
            raw[i + 7 * NB] = (packed[i] >> 7) & 0x1;
        }
    }
};

// 2-bit pack/unpack
template <>
struct ScalarVerticalBlockBitPacker<2> {
    template <size_t BlockSize>
    __attribute__((always_inline)) static void pack(const uint8_t* __restrict__ raw,
                                                    uint8_t* __restrict__ packed) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   raw0 = raw + 0 * NB;
        const uint8_t*   raw1 = raw + 1 * NB;
        const uint8_t*   raw2 = raw + 2 * NB;
        const uint8_t*   raw3 = raw + 3 * NB;
        const uint8_t*   raw4 = raw + 4 * NB;
        const uint8_t*   raw5 = raw + 5 * NB;
        const uint8_t*   raw6 = raw + 6 * NB;
        const uint8_t*   raw7 = raw + 7 * NB;
        uint8_t*         packed0 = packed + 0 * NB;
        uint8_t*         packed1 = packed + 1 * NB;
        for (size_t i = 0; i < NB; ++i) {
            packed0[i] = raw0[i] ^ (raw2[i] << 2) ^ (raw4[i] << 4) ^ (raw6[i] << 6);
            packed1[i] = raw1[i] ^ (raw3[i] << 2) ^ (raw5[i] << 4) ^ (raw7[i] << 6);
        }
    }

    template <size_t BlockSize>
    __attribute__((always_inline)) static void unpack(const uint8_t* __restrict__ packed,
                                                      uint8_t* __restrict__ raw) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   packed0 = packed + 0 * NB;
        const uint8_t*   packed1 = packed + 1 * NB;
        for (size_t i = 0; i < NB; ++i) {
            raw[i + 0 * NB] = (packed0[i] >> 0) & 0x3;
            raw[i + 1 * NB] = (packed1[i] >> 0) & 0x3;
            raw[i + 2 * NB] = (packed0[i] >> 2) & 0x3;
            raw[i + 3 * NB] = (packed1[i] >> 2) & 0x3;
            raw[i + 4 * NB] = (packed0[i] >> 4) & 0x3;
            raw[i + 5 * NB] = (packed1[i] >> 4) & 0x3;
            raw[i + 6 * NB] = (packed0[i] >> 6) & 0x3;
            raw[i + 7 * NB] = (packed1[i] >> 6) & 0x3;
        }
    }
};

// 3-bit pack/unpack
template <>
struct ScalarVerticalBlockBitPacker<3> {
    template <size_t BlockSize>
    __attribute__((always_inline)) static void pack(const uint8_t* __restrict__ raw,
                                                    uint8_t* __restrict__ packed) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   raw0 = raw + 0 * NB;
        const uint8_t*   raw1 = raw + 1 * NB;
        const uint8_t*   raw2 = raw + 2 * NB;
        const uint8_t*   raw3 = raw + 3 * NB;
        const uint8_t*   raw4 = raw + 4 * NB;
        const uint8_t*   raw5 = raw + 5 * NB;
        const uint8_t*   raw6 = raw + 6 * NB;
        const uint8_t*   raw7 = raw + 7 * NB;
        uint8_t*         packed0 = packed + 0 * NB;
        uint8_t*         packed1 = packed + 1 * NB;
        uint8_t*         packed2 = packed + 2 * NB;
        for (size_t i = 0; i < NB; ++i) {
            // Distribute the 6 bits from packed3 across 2 MSBs in packed{0,1,2}
            uint8_t packed3 = raw3[i] ^ (raw7[i] << 3);
            packed0[i] = raw0[i] ^ (raw4[i] << 3) ^ ((packed3 << 2) & 0xC0);
            packed1[i] = raw1[i] ^ (raw5[i] << 3) ^ ((packed3 << 4) & 0xC0);
            packed2[i] = raw2[i] ^ (raw6[i] << 3) ^ ((packed3 << 6) & 0xC0);
        }
    }

    template <size_t BlockSize>
    __attribute__((always_inline)) static void unpack(const uint8_t* __restrict__ packed,
                                                      uint8_t* __restrict__ raw) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   packed0 = packed + 0 * NB;
        const uint8_t*   packed1 = packed + 1 * NB;
        const uint8_t*   packed2 = packed + 2 * NB;
        for (size_t i = 0; i < NB; ++i) {
            raw[i + 0 * NB] = (packed0[i] >> 0) & 0x7;
            raw[i + 1 * NB] = (packed1[i] >> 0) & 0x7;
            raw[i + 2 * NB] = (packed2[i] >> 0) & 0x7;
            // Unpack 2 packed{0,1,2} MSBs into one 6-bit value that represents input raw7
            // (bits 3..5) and raw3 (bits 0..2).
            const uint8_t raw73 = (packed2[i] >> 6) ^ ((packed1[i] & 0xC0) >> 4) ^ ((packed0[i] & 0xC0) >> 2);
            raw[i + 3 * NB] = raw73 & 0x7;
            raw[i + 4 * NB] = (packed0[i] >> 3) & 0x7;
            raw[i + 5 * NB] = (packed1[i] >> 3) & 0x7;
            raw[i + 6 * NB] = (packed2[i] >> 3) & 0x7;
            raw[i + 7 * NB] = (raw73 >> 3) & 0x7;
        }
    }
};

// 4-bit pack/unpack
template <>
struct ScalarVerticalBlockBitPacker<4> {
    template <size_t BlockSize>
    __attribute__((always_inline)) static void pack(const uint8_t* __restrict__ raw,
                                                    uint8_t* __restrict__ packed) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   raw0 = raw + 0 * NB;
        const uint8_t*   raw1 = raw + 1 * NB;
        const uint8_t*   raw2 = raw + 2 * NB;
        const uint8_t*   raw3 = raw + 3 * NB;
        const uint8_t*   raw4 = raw + 4 * NB;
        const uint8_t*   raw5 = raw + 5 * NB;
        const uint8_t*   raw6 = raw + 6 * NB;
        const uint8_t*   raw7 = raw + 7 * NB;
        uint8_t*         packed0 = packed + 0 * NB;
        uint8_t*         packed1 = packed + 1 * NB;
        uint8_t*         packed2 = packed + 2 * NB;
        uint8_t*         packed3 = packed + 3 * NB;
        for (size_t i = 0; i < NB; ++i) {
            packed0[i] = raw0[i] ^ (raw2[i] << 4);
            packed1[i] = raw1[i] ^ (raw3[i] << 4);
            packed2[i] = raw4[i] ^ (raw6[i] << 4);
            packed3[i] = raw5[i] ^ (raw7[i] << 4);
        }
    }

    template <size_t BlockSize>
    __attribute__((always_inline)) static void unpack(const uint8_t* __restrict__ packed,
                                                      uint8_t* __restrict__ raw) noexcept {
        constexpr size_t NB = BlockSize;
        const uint8_t*   packed0 = packed + 0 * NB;
        const uint8_t*   packed1 = packed + 1 * NB;
        const uint8_t*   packed2 = packed + 2 * NB;
        const uint8_t*   packed3 = packed + 3 * NB;
        for (size_t i = 0; i < NB; ++i) {
            raw[i + 0 * NB] = (packed0[i] >> 0) & 0xF;
            raw[i + 1 * NB] = (packed1[i] >> 0) & 0xF;
            raw[i + 2 * NB] = (packed0[i] >> 4) & 0xF;
            raw[i + 3 * NB] = (packed1[i] >> 4) & 0xF;
            raw[i + 4 * NB] = (packed2[i] >> 0) & 0xF;
            raw[i + 5 * NB] = (packed3[i] >> 0) & 0xF;
            raw[i + 6 * NB] = (packed2[i] >> 4) & 0xF;
            raw[i + 7 * NB] = (packed3[i] >> 4) & 0xF;
        }
    }
};

/*
 * Horizontal bit (un-)packing for remainders.
 *
 * Generates the same remainder output as BlockMultiBitPacker, but the code has the added
 * assumption/precondition of no spurious bits set in the input. I.e. less general, but
 * expected to be slightly faster due to less input masking.
 */

template <uint8_t Bits>
struct ScalarHorizontalRemainderBitPacker;

/*
 * 1-bit horizontal packed layout (1 byte):
 *
 *    byte 0
 * | 7654 3210 |
 *
 */
template <>
struct ScalarHorizontalRemainderBitPacker<1> {
    __attribute__((always_inline)) static void pack1_to_7(const uint8_t* __restrict__ src, uint8_t* __restrict__ dst,
                                                          const size_t n) noexcept {
        *dst = src[0]; // Must ensure `dst` is initialized before XORing in anything
        for (size_t i = 1; i < n; ++i) {
            *dst ^= src[i] << i;
        }
    }

    __attribute__((always_inline)) static void unpack1_to_7(const uint8_t* __restrict__ src,
                                                            uint8_t* __restrict__ dst, const size_t n) noexcept {
        for (size_t i = 0; i < n; ++i) {
            dst[i] = (*src >> i) & 0x1;
        }
    }
};

/*
 * 2-bit horizontal packed layout (1-2 bytes):
 *
 *    byte 0      byte 1
 * | 3322 1100 | 7766 5544 |
 */
template <>
struct ScalarHorizontalRemainderBitPacker<2> {
    __attribute__((always_inline)) static void pack1_to_7(const uint8_t* __restrict__ src, uint8_t* __restrict__ dst,
                                                          const size_t n) noexcept {
        dst[0] = src[0] << 0;
        if (n == 1) {
            return;
        }
        dst[0] ^= src[1] << 2;
        if (n == 2) {
            return;
        }
        dst[0] ^= src[2] << 4;
        if (n == 3) {
            return;
        }
        dst[0] ^= src[3] << 6;
        if (n == 4) {
            return;
        }
        dst[1] = src[4] << 0;
        if (n == 5) {
            return;
        }
        dst[1] ^= src[5] << 2;
        if (n == 6) {
            return;
        }
        dst[1] ^= src[6] << 4;
        if (n == 7) {
            return;
        }
        dst[1] ^= src[7] << 6;
    }

    __attribute__((always_inline)) static void unpack1_to_7(const uint8_t* __restrict__ src,
                                                            uint8_t* __restrict__ dst, const size_t n) noexcept {
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
 * 3-bit horizontal packed layout (1-3 bytes):
 *
 *    byte 0      byte 1      byte 2
 * | 2211 1000 | 5444 3332 | 7776 6655 |
 */
template <>
struct ScalarHorizontalRemainderBitPacker<3> {
    __attribute__((always_inline)) static void pack1_to_7(const uint8_t* __restrict__ src, uint8_t* __restrict__ dst,
                                                          const size_t n) noexcept {
        uint32_t tmp = 0;
        for (size_t i = 0; i < n; ++i) {
            tmp ^= src[i] << (3 * i);
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

    __attribute__((always_inline)) static void unpack1_to_7(const uint8_t* __restrict__ src,
                                                            uint8_t* __restrict__ dst, const size_t n) noexcept {
        uint32_t tmp = src[0];
        if (n > 2) {
            tmp ^= src[1] << 8;
        }
        if (n > 5) {
            tmp ^= src[2] << 16;
        }
        for (size_t i = 0; i < n; ++i) {
            dst[i] = (tmp >> (3 * i)) & 0x7;
        }
    }
};

/*
 * 4-bit horizontal packed layout (1-4 bytes):
 *
 *    byte 0      byte 1      byte 2      byte 3
 * | 1111 0000 | 3333 2222 | 5555 4444 | 7777 6666 |
 */
template <>
struct ScalarHorizontalRemainderBitPacker<4> {
    __attribute__((always_inline)) static void pack1_to_7(const uint8_t* __restrict__ src, uint8_t* __restrict__ dst,
                                                          const size_t n) noexcept {
        dst[0] = src[0] << 0;
        if (n == 1) {
            return;
        }
        dst[0] ^= src[1] << 4;
        if (n == 2) {
            return;
        }
        dst[1] = src[2] << 0;
        if (n == 3) {
            return;
        }
        dst[1] ^= src[3] << 4;
        if (n == 4) {
            return;
        }
        dst[2] = src[4] << 0;
        if (n == 5) {
            return;
        }
        dst[2] ^= src[5] << 4;
        if (n == 6) {
            return;
        }
        dst[3] = src[6] << 0;
        if (n == 7) {
            return;
        }
        dst[3] ^= src[7] << 4;
    }

    __attribute__((always_inline)) static void unpack1_to_7(const uint8_t* __restrict__ src,
                                                            uint8_t* __restrict__ dst, const size_t n) noexcept {
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

/*
 * Bit packing of an arbitrary `sz` number of input u8 integers from `raw`, that each MUST have
 * an unsigned value _less than_ 2**Bits.
 *
 * `packed` must be valid for at least `packed_bytes(Bits, sz)` bytes.
 *
 * We use vertical packing with blocks of 128 input integers until this is no longer possible
 * due to insufficient remaining input. The leftovers are then processed with a fixed sequence
 * of blocks, each block being 1/2 the size of the previous block, until we reach the smallest
 * possible block size (8). Any remaining [0, 7] elements are then packed _horizontally_ at
 * the very end of the buffer.
 *
 * The packed output byte count of any input shall be identical to that of MultiBitPacker.
 */
template <uint8_t Bits>
void scalar_block_bit_pack(const uint8_t* __restrict__ raw, uint8_t* __restrict__ packed, const size_t sz) noexcept {
    static_assert(Bits >= 1 && Bits <= 4, "Unsupported bit count");
    constexpr uint8_t B = Bits;
    using BlockBitPacker = ScalarVerticalBlockBitPacker<B>;
    using RemainderBitPacker = ScalarHorizontalRemainderBitPacker<B>;

    size_t i = 0;
    for (; i + 128 <= sz; i += 128, packed += 16 * B) {
        BlockBitPacker::template pack<16>(raw + i, packed);
    }
    size_t rem = sz - i;
    if (rem >= 64) { // <= 127
        BlockBitPacker::template pack<8>(raw + i, packed);
        i += 64;
        packed += 8 * B;
        rem -= 64;
    }
    if (rem >= 32) { // <= 63
        BlockBitPacker::template pack<4>(raw + i, packed);
        i += 32;
        packed += 4 * B;
        rem -= 32;
    }
    if (rem >= 16) { // <= 31
        BlockBitPacker::template pack<2>(raw + i, packed);
        i += 16;
        packed += 2 * B;
        rem -= 16;
    }
    if (rem >= 8) { // <= 15
        BlockBitPacker::template pack<1>(raw + i, packed);
        i += 8;
        packed += 1 * B;
        rem -= 8;
    }
    // For the final non-block remainder, use _horizontal_ packing to avoid having
    // to pad with up to B-1 zero-bytes. The `rem < 8` condition is a tautology, but
    // adding it helps the compiler understand that `rem` is in the range [1, 7]
    // when inlining `pack1_to_7`. This gives better unrolling codegen since any
    // loop induction variable within it will necessarily be bounded by `rem`.
    if (rem > 0 && rem < 8) { // <= 7
        RemainderBitPacker::pack1_to_7(raw + i, packed, rem);
    }
}

/*
 * Unpack `sz` _integers_ (_not_ bits!) from `packed` into `raw`.
 *
 * `packed` must be valid for at least `packed_bytes(Bits, sz)` bytes and `raw` must be
 * valid for at least `sz` bytes.
 */
template <uint8_t Bits>
void scalar_block_bit_unpack(const uint8_t* __restrict__ packed, uint8_t* __restrict__ raw,
                             const size_t sz) noexcept {
    static_assert(Bits >= 1 && Bits <= 4, "Unsupported bit count");
    constexpr uint8_t B = Bits;
    using BlockBitPacker = ScalarVerticalBlockBitPacker<B>;
    using RemainderBitPacker = ScalarHorizontalRemainderBitPacker<B>;

    size_t i = 0;
    for (; i + 128 <= sz; i += 128, packed += 16 * B) {
        BlockBitPacker::template unpack<16>(packed, raw + i);
    }
    size_t rem = sz - i;
    if (rem >= 64) { // <= 127
        BlockBitPacker::template unpack<8>(packed, raw + i);
        i += 64;
        packed += 8 * B;
        rem -= 64;
    }
    if (rem >= 32) { // <= 63
        BlockBitPacker::template unpack<4>(packed, raw + i);
        i += 32;
        packed += 4 * B;
        rem -= 32;
    }
    if (rem >= 16) { // <= 31
        BlockBitPacker::template unpack<2>(packed, raw + i);
        i += 16;
        packed += 2 * B;
        rem -= 16;
    }
    if (rem >= 8) { // <= 15
        BlockBitPacker::template unpack<1>(packed, raw + i);
        i += 8;
        packed += 1 * B;
        rem -= 8;
    }
    if (rem > 0 && rem < 8) { // <= 7
        RemainderBitPacker::unpack1_to_7(packed, raw + i, rem);
    }
}

} // namespace vespalib::hwaccelerated
