// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Avoid include guard linter warnings
#ifndef VESPA_HWY_BLOCK_BIT_PACKING_INL_H_CALM_DOWN_LINTER
#define VESPA_HWY_BLOCK_BIT_PACKING_INL_H_CALM_DOWN_LINTER
#endif

// Per-target include guard. See https://google.github.io/highway/en/master/faq.html Q5.4.
#if defined(VESPA_HWY_BLOCK_BIT_PACKING_INL_H_TARGET) == defined(HWY_TARGET_TOGGLE)
#ifdef VESPA_HWY_BLOCK_BIT_PACKING_INL_H_TARGET
#undef VESPA_HWY_BLOCK_BIT_PACKING_INL_H_TARGET
#else
#define VESPA_HWY_BLOCK_BIT_PACKING_INL_H_TARGET
#endif

#include "hwy_kernel-inl.h"
#include "hwy_kernel_dispatch-inl.h"
#include "hwy_vec_unpack-inl.h"
#include "invocation_meta.h"

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;

template <uint8_t Bits, typename KernelFn, size_t FnAccus, typename... Accus>
void unary_unpack_bits(const uint8_t* HWY_RESTRICT packed, const size_t sz, KernelFn kernel_fn,
                       const FnAccuArity<FnAccus>, Accus&&... accus) {
    constexpr uint8_t B = Bits;

    const hn::Full128<uint8_t> d8x16;
    const hn::Full64<uint8_t>  d8x8;
    const hn::Full32<uint8_t>  d8x4;
    const hn::Full16<uint8_t>  d8x2;

    static_assert(FnAccus <= sizeof...(Accus), "Can't use more accumulators than actually exist");
    constexpr auto arity = AccusAndFnArity<sizeof...(Accus), FnAccus>{};

    size_t i = 0;
    for (; i + 128 <= sz; i += 128, packed += 16 * B) {
        hn::Vec<decltype(d8x16)> raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7;
        VecUnpack8<B>::unpack(d8x16, packed, raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7);
        // We now have 8x16 u8 vectors filled with fun and sunshine. The kernel function accepts
        // one such vector at a time, so we manually do 8x dispatches. The BlockInvocation index
        // allows for transparent striping of input accumulators (if any) across kernel
        // invocations via my_dispatch.
        // TODO specialize this for >128-bit vector targets...!
        my_dispatch(BlockInvocation<0>{}, arity, kernel_fn, raw0, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<1>{}, arity, kernel_fn, raw1, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<2>{}, arity, kernel_fn, raw2, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<3>{}, arity, kernel_fn, raw3, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<4>{}, arity, kernel_fn, raw4, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<5>{}, arity, kernel_fn, raw5, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<6>{}, arity, kernel_fn, raw6, std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<7>{}, arity, kernel_fn, raw7, std::forward<Accus>(accus)...);
    }
    // Remainder blocks halve their size (and thus lane count) for each "step". Since the
    // kernel function expects a 128-bit vector, we maintain the full block illusion by gluing
    // together vector registers into larger registers across fewer kernel calls. It takes 2x
    // the glue and 1/2 the kernel calls per remainder block size reduction step.
    size_t rem = sz - i;
    if (rem >= 64) { // <= 127
        hn::Vec<decltype(d8x8)> raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7;
        VecUnpack8<B>::unpack(d8x8, packed, raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7);
        // raw1, raw0 in that order since Combine's first arg is the _high_ part of the destination
        my_dispatch(BlockInvocation<0>{}, arity, kernel_fn, hn::Combine(d8x16, raw1, raw0),
                    std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<1>{}, arity, kernel_fn, hn::Combine(d8x16, raw3, raw2),
                    std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<2>{}, arity, kernel_fn, hn::Combine(d8x16, raw5, raw4),
                    std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<3>{}, arity, kernel_fn, hn::Combine(d8x16, raw7, raw6),
                    std::forward<Accus>(accus)...);
        i += 64;
        packed += 8 * B;
        rem -= 64;
    }
    if (rem >= 32) { // <= 63
        hn::Vec<decltype(d8x4)> raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7;
        VecUnpack8<B>::unpack(d8x4, packed, raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7);
        my_dispatch(BlockInvocation<0>{}, arity, kernel_fn,
                    hn::Combine(d8x16, hn::Combine(d8x8, raw3, raw2), hn::Combine(d8x8, raw1, raw0)),
                    std::forward<Accus>(accus)...);
        my_dispatch(BlockInvocation<1>{}, arity, kernel_fn,
                    hn::Combine(d8x16, hn::Combine(d8x8, raw7, raw6), hn::Combine(d8x8, raw5, raw4)),
                    std::forward<Accus>(accus)...);
        i += 32;
        packed += 4 * B;
        rem -= 32;
    }
    if (rem >= 16) { // <= 31
        hn::Vec<decltype(d8x2)> raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7;
        VecUnpack8<B>::unpack(d8x2, packed, raw0, raw1, raw2, raw3, raw4, raw5, raw6, raw7);
        my_dispatch(BlockInvocation<0>{}, arity, kernel_fn,
                    hn::Combine(d8x16,
                                hn::Combine(d8x8, hn::Combine(d8x4, raw7, raw6), hn::Combine(d8x4, raw5, raw4)),
                                hn::Combine(d8x8, hn::Combine(d8x4, raw3, raw2), hn::Combine(d8x4, raw1, raw0))),
                    std::forward<Accus>(accus)...);
        i += 16;
        packed += 2 * B;
        rem -= 16;
    }
    // For the final non-SIMD _unpacking_ remainder (if any), we go via a temporary buffer.
    // This may cause some store-to-load forwarding stalls for the final Load(), but that
    // doesn't really matter since this is outside the hot path.
    if (rem > 0) { // <= 15
        alignas(16) uint8_t tmp_raw[16] = {};
        const size_t        orig_rem = rem;
        i = 0;
        if (rem >= 8) {
            ScalarVerticalBlockBitPacker<B>::template unpack<1>(packed, tmp_raw);
            i = 8;
            packed += 1 * B;
            rem -= 8;
        }
        if (rem > 0 && rem < 8) { // <= 7
            ScalarHorizontalRemainderBitPacker<B>::unpack1_to_7(packed, tmp_raw + i, rem);
        }
        // Load() may fault if unaligned, so tmp_raw must have vector alignment
        my_dispatch(PartialInvocation{orig_rem}, arity, kernel_fn, hn::Load(d8x16, tmp_raw),
                    std::forward<Accus>(accus)...);
    }
}

} // namespace HWY_NAMESPACE
} // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#endif // VESPA_HWY_BLOCK_BIT_PACKING_INL_H_TARGET
