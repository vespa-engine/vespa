// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// This file contains our own extensions to Highway's API for specific operations where
// there is no existing functionality that maps to the instructions that we want.

// Avoid include guard linter warnings
#ifndef VESPA_HWY_AUX_OPS_INL_H_CALM_DOWN_LINTER
#define VESPA_HWY_AUX_OPS_INL_H_CALM_DOWN_LINTER
#endif

// Per-target include guard. See https://google.github.io/highway/en/master/faq.html Q5.4.
#if defined(VESPA_HWY_AUX_OPS_INL_H_TARGET) == defined(HWY_TARGET_TOGGLE)
#ifdef VESPA_HWY_AUX_OPS_INL_H_TARGET
#undef VESPA_HWY_AUX_OPS_INL_H_TARGET
#else
#define VESPA_HWY_AUX_OPS_INL_H_TARGET
#endif

#include <hwy/highway.h>

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;

#if HWY_TARGET == HWY_SVE2 || HWY_TARGET == HWY_SVE2_128

// Both GCC and Clang will under NEON peephole-optimize the sequence
//
//   vsubq_s16(vmovl_high_s8(A), vmovl_high_s8(B))
//
// into
//
//   vsubl_high_s8(A, B)
//
// I.e. _explicit_ widening of high lanes from i8->i16 on both A and B followed by
// a non-widening subtraction is rewritten to a subtraction with _implicitly_
// widened high vector lanes. This is quite a bit more performant since it collapses
// 3 distinct instructions (with temporary register usage) into a single instruction.
// Since Highway emits the first sequence of instructions, we are dependent on the
// compiler doing this optimization.
//
// Unfortunately, this does not apply to SVE2 (SVE does not have such a widening
// SSUBL instruction at all, so nothing we can do there); we are left with the explicit
// widening instructions. This is likely because the SVE widening subtraction works
// on even/odd lanes and not upper/lower lanes. Moving to PromoteOdd/Even instead of
// Upper/Lower does not seem to trigger any optimizations.
//
// To get around this, we specialize our own ReorderWidenSub operation which on SVE2
// and SVE2_128 will explicitly generate SSUBLB/SSUBLT instructions. The rationale
// for calling this _reordered_ widened subtraction is that, as mentioned, SVE2
// widening subtraction works on even/odd lanes, whereas NEON works on upper/lower
// lanes, so we want to make it clear that the end result represents the subtraction
// of some arbitrary, but well-defined, pairwise permutation of lanes.
//
// Consequently, using this means that you cannot depend on the ordering of the lanes
// in the output vectors, just that they represent _some_ pairwise subtraction.

// Using raw SVE types mirrors how overloads are done in Highway's `arm_sve-inl.h`
template <size_t N, int kPow2>
HWY_INLINE
void ReorderWidenSub(hn::Simd<int16_t, N, kPow2>,
                     svint8_t a, svint8_t b,
                     svint16_t& sub0, svint16_t& sub1) noexcept
{
    sub0 = svsublt_s16(a, b); // Top (odd) lanes
    sub1 = svsublb_s16(a, b); // Bottom (even) lanes
}

#endif

// Generic fallback for reordered widening subtraction
template <typename D, typename V>
HWY_INLINE
void ReorderWidenSub(D d, V a, V b, hn::VFromD<D>& sub0, hn::VFromD<D>& sub1) noexcept {
    sub0 = hn::Sub(hn::PromoteLowerTo(d, a), hn::PromoteLowerTo(d, b));
    sub1 = hn::Sub(hn::PromoteUpperTo(d, a), hn::PromoteUpperTo(d, b));
}

} // HWY_NAMESPACE
} // vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#endif // VESPA_HWY_AUX_OPS_INL_H_TARGET
