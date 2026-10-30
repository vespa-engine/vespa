// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Based on code from the Highway bit packing kernels (contrib/bit_pack/bit_pack-inl.h), Copyright 2022 Google LLC.
// Google Highway is licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Avoid include guard linter warnings
#ifndef VESPA_HWY_VEC_UNPACK_INL_H_CALM_DOWN_LINTER
#define VESPA_HWY_VEC_UNPACK_INL_H_CALM_DOWN_LINTER
#endif

// Per-target include guard. See https://google.github.io/highway/en/master/faq.html Q5.4.
#if defined(VESPA_HWY_VEC_UNPACK_INL_H_TARGET) == defined(HWY_TARGET_TOGGLE)
#ifdef VESPA_HWY_VEC_UNPACK_INL_H_TARGET
#undef VESPA_HWY_VEC_UNPACK_INL_H_TARGET
#else
#define VESPA_HWY_VEC_UNPACK_INL_H_TARGET
#endif

#include <hwy/highway.h>

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;

// For now we only care about _unpacking_ to registers. Packing only happens
// as part of the quantization process, which is once per vector, and in that
// case we _do_ want to pack directly to a memory buffer.
// These are in essence the Highway Pack<Bits> unpacking kernels, but instead
// of storing to memory, they store directly to output vector registers.

template <uint8_t Bits>
struct VecUnpack8;

template <>
struct VecUnpack8<1> {
    template <class D8>
    HWY_API void unpack(D8 d8, const uint8_t* HWY_RESTRICT packed_in, hn::VFromD<D8>& raw0, hn::VFromD<D8>& raw1,
                        hn::VFromD<D8>& raw2, hn::VFromD<D8>& raw3, hn::VFromD<D8>& raw4, hn::VFromD<D8>& raw5,
                        hn::VFromD<D8>& raw6, hn::VFromD<D8>& raw7) noexcept {
        const hn::RepartitionToWide<decltype(d8)> d16;
        using VU16 = hn::Vec<decltype(d16)>;

        const VU16 mask = hn::Set(d16, 0x0101u); // LSB in each byte
        const VU16 packed = BitCast(d16, hn::LoadU(d8, packed_in));

        raw0 = hn::BitCast(d8, hn::And(packed, mask));
        raw1 = hn::BitCast(d8, hn::And(hn::ShiftRight<1>(packed), mask));
        raw2 = hn::BitCast(d8, hn::And(hn::ShiftRight<2>(packed), mask));
        raw3 = hn::BitCast(d8, hn::And(hn::ShiftRight<3>(packed), mask));
        raw4 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed), mask));
        raw5 = hn::BitCast(d8, hn::And(hn::ShiftRight<5>(packed), mask));
        raw6 = hn::BitCast(d8, hn::And(hn::ShiftRight<6>(packed), mask));
        raw7 = hn::BitCast(d8, hn::And(hn::ShiftRight<7>(packed), mask));
    }
};

template <>
struct VecUnpack8<2> {
    template <class D8>
    HWY_API void unpack(D8 d8, const uint8_t* HWY_RESTRICT packed_in, hn::VFromD<D8>& raw0, hn::VFromD<D8>& raw1,
                        hn::VFromD<D8>& raw2, hn::VFromD<D8>& raw3, hn::VFromD<D8>& raw4, hn::VFromD<D8>& raw5,
                        hn::VFromD<D8>& raw6, hn::VFromD<D8>& raw7) noexcept {
        const hn::RepartitionToWide<decltype(d8)> d16;
        using VU16 = hn::Vec<decltype(d16)>;
        HWY_LANES_CONSTEXPR size_t N8 = hn::Lanes(d8);

        const VU16 mask = hn::Set(d16, 0x0303u); // Lowest 2 bits per byte
        const VU16 packed0 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 0 * N8));
        const VU16 packed1 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 1 * N8));

        raw0 = hn::BitCast(d8, hn::And(packed0, mask));
        raw1 = hn::BitCast(d8, hn::And(packed1, mask));
        raw2 = hn::BitCast(d8, hn::And(hn::ShiftRight<2>(packed0), mask));
        raw3 = hn::BitCast(d8, hn::And(hn::ShiftRight<2>(packed1), mask));
        raw4 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed0), mask));
        raw5 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed1), mask));
        raw6 = hn::BitCast(d8, hn::And(hn::ShiftRight<6>(packed0), mask));
        raw7 = hn::BitCast(d8, hn::And(hn::ShiftRight<6>(packed1), mask));
    }
};

template <>
struct VecUnpack8<3> {
    template <class D8>
    HWY_API void unpack(D8 d8, const uint8_t* HWY_RESTRICT packed_in, hn::VFromD<D8>& raw0, hn::VFromD<D8>& raw1,
                        hn::VFromD<D8>& raw2, hn::VFromD<D8>& raw3, hn::VFromD<D8>& raw4, hn::VFromD<D8>& raw5,
                        hn::VFromD<D8>& raw6, hn::VFromD<D8>& raw7) noexcept {
        const hn::RepartitionToWide<decltype(d8)> d16;
        using VU16 = hn::Vec<decltype(d16)>;
        HWY_LANES_CONSTEXPR size_t N8 = hn::Lanes(d8);

        const VU16 mask = hn::Set(d16, 0x0707u); // Lowest 3 bits per byte
        const VU16 packed0 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 0 * N8));
        const VU16 packed1 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 1 * N8));
        const VU16 packed2 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 2 * N8));

        raw0 = hn::BitCast(d8, hn::And(packed0, mask));
        raw1 = hn::BitCast(d8, hn::And(packed1, mask));
        raw2 = hn::BitCast(d8, hn::And(packed2, mask));
        raw4 = hn::BitCast(d8, hn::And(hn::ShiftRight<3>(packed0), mask));
        raw5 = hn::BitCast(d8, hn::And(hn::ShiftRight<3>(packed1), mask));
        raw6 = hn::BitCast(d8, hn::And(hn::ShiftRight<3>(packed2), mask));
        // raw73 is the concatenation of the upper two bits in packed0..2.
        const VU16 hi2 = hn::Set(d16, 0xC0C0u);
        const VU16 raw73 =
            hn::Xor3(hn::ShiftRight<6>(hn::And(packed2, hi2)), hn::ShiftRight<4>(hn::And(packed1, hi2)),
                     hn::ShiftRight<2>(hn::And(packed0, hi2)));

        raw3 = hn::BitCast(d8, hn::And(mask, raw73));
        raw7 = hn::BitCast(d8, hn::And(mask, hn::ShiftRight<3>(raw73)));
    }
};

template <>
struct VecUnpack8<4> {
    template <class D8>
    HWY_API void unpack(D8 d8, const uint8_t* HWY_RESTRICT packed_in, hn::VFromD<D8>& raw0, hn::VFromD<D8>& raw1,
                        hn::VFromD<D8>& raw2, hn::VFromD<D8>& raw3, hn::VFromD<D8>& raw4, hn::VFromD<D8>& raw5,
                        hn::VFromD<D8>& raw6, hn::VFromD<D8>& raw7) noexcept {
        const hn::RepartitionToWide<decltype(d8)> d16;
        using VU16 = hn::Vec<decltype(d16)>;
        HWY_LANES_CONSTEXPR size_t N8 = hn::Lanes(d8);

        const VU16 mask = hn::Set(d16, 0x0F0Fu); // Lowest 4 bits per byte
        const VU16 packed0 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 0 * N8));
        const VU16 packed1 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 1 * N8));
        const VU16 packed2 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 2 * N8));
        const VU16 packed3 = hn::BitCast(d16, hn::LoadU(d8, packed_in + 3 * N8));

        raw0 = hn::BitCast(d8, hn::And(packed0, mask));
        raw1 = hn::BitCast(d8, hn::And(packed1, mask));
        raw2 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed0), mask));
        raw3 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed1), mask));
        raw4 = hn::BitCast(d8, hn::And(packed2, mask));
        raw5 = hn::BitCast(d8, hn::And(packed3, mask));
        raw6 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed2), mask));
        raw7 = hn::BitCast(d8, hn::And(hn::ShiftRight<4>(packed3), mask));
    }
};

} // namespace HWY_NAMESPACE
} // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#endif // VESPA_HWY_VEC_UNPACK_INL_H_TARGET
