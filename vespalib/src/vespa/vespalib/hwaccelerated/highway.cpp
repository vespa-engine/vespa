// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Hack: make Highway understand that we're using an IDE if CLion is present
// TODO upstream to Highway HWY_IDE check
#if defined(__CLION_IDE__) && !defined(__INTELLISENSE__)
#define __INTELLISENSE__ 1
#endif

#include "fn_table.h"
#include "float8_luts.h"
#include "highway.h"
#include "platform_generic.h"
#include "target_info.h"
#include <hwy/base.h>
#include <algorithm>
#include <bit>
#include <cassert>
#include <format>

// This file will be recursively included into itself with different target
// compilation parameters. See the Highway docs.
#undef HWY_TARGET_INCLUDE
#define HWY_TARGET_INCLUDE "vespa/vespalib/hwaccelerated/highway.cpp"
#include <hwy/foreach_target.h>

#include "hwy_kernel-inl.h"
#include "hwy_aux_ops-inl.h"
#include <hwy/contrib/dot/dot-inl.h>

// TODO temp
#include <hwy/print-inl.h>

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;
namespace {
// Must always be undef'd to ensure we expand the correct HWY_ATTR per target.
#undef VESPA_HWY_LAMBDA
#if defined(__CLION_IDE__)
#define VESPA_HWY_LAMBDA HWY_ATTR // CLion gets confused with multiple attributes on lambdas
#elif defined(__clang__)
// Clang and GCC refuse to parse `noexcept HWY_ATTR` and `HWY_ATTR noexcept`,
// respectively. GCC seems to be the one that is technically correct(tm), but
// we still want Clang to compile, so hide the dirt under a macro carpet.
// Force lambda caller inlining to avoid GCC doing some wild codegen with vector
// register spilling to stack temporaries if it decides to break the lambda out
// as a separate logical function. For good measure, also ensure that we inline
// the lambda's _callees_. This mirrors the most prudent parts of HWY_API.
// `HWY_ATTR` is needed to ensure lambdas have the expected codegen target.
// See https://google.github.io/highway/en/master/faq.html#boilerplate
#define VESPA_HWY_LAMBDA HWY_ATTR __attribute__((always_inline, flatten)) noexcept
#else
#define VESPA_HWY_LAMBDA noexcept HWY_ATTR __attribute__((always_inline, flatten))
#endif

// Many of the Highway functions used within this file are fairly self-explanatory
// of how they relate to elements in, and across, vectors (Sub, Mul, MulAdd etc.),
// others not so much (ReorderWidenMulAccumulate, SumOfMulQuadAccumulate, ...).
// Please refer to https://google.github.io/highway/en/master/quick_reference.html
// for the exact semantics of such functions.

template <typename T, typename R = T>
requires (hwy::IsFloat<T>() || hwy::IsSame<T, hwy::bfloat16_t>())
HWY_INLINE
R my_hwy_dot_impl(const T* HWY_RESTRICT a,
                  const T* HWY_RESTRICT b,
                  const size_t sz) noexcept
{
    const hn::ScalableTag<T> d;
    return hwy::ConvertScalarTo<R>(hn::Dot::Compute<0>(d, a, b, sz));
}

HWY_INLINE
float my_hwy_dot_float(const float* HWY_RESTRICT a,
                       const float* HWY_RESTRICT b,
                       const size_t sz) noexcept
{
    return my_hwy_dot_impl(a, b, sz);
}

HWY_INLINE
double my_hwy_dot_double(const double* HWY_RESTRICT a,
                         const double* HWY_RESTRICT b,
                         const size_t sz) noexcept
{
    return my_hwy_dot_impl(a, b, sz);
}

// Although Highway comes with its own BFloat16 dot product kernel, we do not use it due
// to some very unfortunate codegen by GCC for the code handling the edge case where
// input vectors are shorter than the number of lanes of a single vector register.
//
// This is done using a loop with scalar (i.e. non-vector) conversions from the compiler
// native `__bf16` type to `float` using `static_cast`, which one could reasonably assume
// would be optimal (and Highway presumably does just this). However, GCC will, in a case
// of signalling vs. quiet NaN-handling pedantry, implicitly insert a **function call**
// to the `__extendbfsf2` library function per lhs and rhs element. This brutally destroys
// performance on x64 AVX2+3 for very short vectors, meaning we're better off with our
// own kernel.
//
// It may be noted that Clang has the expected codegen for `__bf16` -> `float` conversions,
// i.e. a simple zero-extending left shift of 16 (see https://reviews.llvm.org/D151563).
//
// See https://gcc.gnu.org/bugzilla/show_bug.cgi?id=121853 for upstream report.
HWY_INLINE
float my_hwy_dot_bf16(const BFloat16* HWY_RESTRICT a,
                      const BFloat16* HWY_RESTRICT b,
                      const size_t sz) noexcept
{
    static_assert(sizeof(BFloat16)  == sizeof(hwy::bfloat16_t));
    static_assert(alignof(BFloat16) == alignof(hwy::bfloat16_t));
    // We make the assumption that both vespalib::BFloat16 and hwy::bfloat16_t are POD-like
    // wrappers around the same u16 bitwise representation, with zero padding bits, meaning
    // we can treat them as-if identical.
    const auto* a_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(a);
    const auto* b_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(b);

    const hn::ScalableTag<hwy::bfloat16_t> dbf16;
    // Repartition to float vector with same vector size, but different number of lanes.
    // E.g. for a 128-bit vector of 8x BF16 lanes this becomes a 4x float lanes vector.
    const hn::Repartition<float, decltype(dbf16)> df32;
    // Since we're widening the element type, loading e.g. 8 lanes of BF16 in a single
    // 128-bit vector requires us to process 2 vectors of 4 lanes of float32.
    auto kernel_fn = [df32](auto lhs, auto rhs, auto& acc0, auto& acc1) VESPA_HWY_LAMBDA {
        acc0 = hn::ReorderWidenMulAccumulate(df32, lhs, rhs, acc0, acc1);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<4>, HasAccumulatorArity<2>>;
    return MyKernel::pairwise(dbf16, df32, a_bf16, b_bf16, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

template <typename T> requires (hwy::IsFloat<T>())
HWY_INLINE
double my_hwy_square_euclidean_distance(const T* HWY_RESTRICT a,
                                        const T* HWY_RESTRICT b,
                                        const size_t sz) noexcept
{
    const hn::ScalableTag<T> d;
    const auto kernel_fn = [](auto lhs, auto rhs, auto& accu) VESPA_HWY_LAMBDA {
        const auto sub = hn::Sub(lhs, rhs);
        accu = hn::MulAdd(sub, sub, accu); // note: using fused multiply-add
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d, d, a, b, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
}

HWY_INLINE
double my_hwy_square_euclidean_distance_bf16(const BFloat16* HWY_RESTRICT a,
                                             const BFloat16* HWY_RESTRICT b,
                                             const size_t sz) noexcept
{
    static_assert(sizeof(BFloat16)  == sizeof(hwy::bfloat16_t));
    static_assert(alignof(BFloat16) == alignof(hwy::bfloat16_t));

    const auto* a_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(a);
    const auto* b_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(b);

    const hn::ScalableTag<hwy::bfloat16_t>        dbf16;
    const hn::Repartition<float, decltype(dbf16)> df;

    auto kernel_fn = [df](auto lhs, auto rhs, auto& acc0, auto& acc1) VESPA_HWY_LAMBDA {
        hn::Vec<decltype(df)> sub_lo, sub_hi;
        ReorderWidenSub(df, lhs, rhs, sub_lo, sub_hi);
        acc0 = hn::MulAdd(sub_lo, sub_lo, acc0);
        acc1 = hn::MulAdd(sub_hi, sub_hi, acc1);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<4>, UnrolledBy<2>, HasAccumulatorArity<2>>;
    return MyKernel::pairwise(dbf16, df, a_bf16, b_bf16, sz, hn::Zero(df), kernel_fn, VecAdd(), LaneReduceSum());
}

// Widen i8 to i16 and subtract. Widen i16 intermediate result to i32 and multiply.
// Important: `sz` should be low enough that the intermediate i32 sum does not overflow!
HWY_INLINE
int32_t sub_mul_add_i8_to_i32(const int8_t* HWY_RESTRICT a,
                              const int8_t* HWY_RESTRICT b,
                              const size_t sz)
{
    const hn::ScalableTag<int8_t>                  d8;
    const hn::Repartition<int16_t, decltype(d8)>  d16;
    const hn::Repartition<int32_t, decltype(d16)> d32;

    auto kernel_fn = [d16, d32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_HWY_LAMBDA {
        hn::Vec<decltype(d16)> sub_l_i16, sub_u_i16;
        ReorderWidenSub(d16, lhs, rhs, sub_l_i16, sub_u_i16);
        acc0 = hn::ReorderWidenMulAccumulate(d32, sub_l_i16, sub_l_i16, acc0, acc1);
        acc2 = hn::ReorderWidenMulAccumulate(d32, sub_u_i16, sub_u_i16, acc2, acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<4>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), kernel_fn, VecAdd(), LaneReduceSum());
}

HWY_INLINE
double my_hwy_square_euclidean_distance_int8(const int8_t* HWY_RESTRICT a,
                                             const int8_t* HWY_RESTRICT b,
                                             const size_t sz) noexcept
{
    // If we cannot possibly overflow intermediate i32 accumulators we can directly
    // compute the distance without requiring any chunking. Max chunk size is defined
    // by the number of worst-case sums of +/-255**2 that can fit into an i32.
    // +/-255 is due to widening subtraction so that the max is 127 - (-128) or (-127) - 128.
    constexpr size_t max_n_per_chunk = INT32_MAX / (255 * 255);
    return compute_chunked_sum<max_n_per_chunk, double>(sub_mul_add_i8_to_i32, a, b, sz);
}

// Performance note: AVX2 and AVX3 do not have dedicated vector popcount instructions,
// so the Highway "emulation" ends up being slower in practice than the baseline one
// using 4x pipelined POPCNT.
HWY_INLINE
size_t my_hwy_popcount(const uint64_t* a, const size_t sz) noexcept {
    const hn::ScalableTag<uint64_t> d;
    const auto kernel_fn = [](auto v, auto& accu) VESPA_HWY_LAMBDA {
        accu = hn::Add(hn::PopulationCount(v), accu);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::elementwise(d, d, a, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
}

// Performance note: will be slower on AVX2/AVX3; see `my_hwy_popcount`.
HWY_INLINE
size_t my_hwy_binary_hamming_distance(const void* HWY_RESTRICT untyped_lhs,
                                      const void* HWY_RESTRICT untyped_rhs,
                                      const size_t sz) noexcept
{
    // Inputs may have arbitrary byte alignments, so we have to read with an 8-bit
    // type to ensure we don't violate any natural alignment requirements. The
    // `HwyReduceKernel` code uses unaligned vector loads, so this works fine and
    // with effectively the same performance regardless of input alignment.
    // We then do the vector equivalent of reinterpret_cast (which is well-defined)
    // to treat each set of 8x 8-bit lanes as 1x u64 lane before running 64-bit
    // xor -> popcount -> accumulate operations.
    const auto* lhs_u8 = static_cast<const uint8_t*>(untyped_lhs);
    const auto* rhs_u8 = static_cast<const uint8_t*>(untyped_rhs);

    const hn::ScalableTag<uint8_t> d8;
    const hn::RepartitionToWideX3<decltype(d8)> d64;

    const auto kernel_fn = [d64](auto lhs, auto rhs, auto& accu) VESPA_HWY_LAMBDA {
        auto lhs_u64 = hn::BitCast(d64, lhs);
        auto rhs_u64 = hn::BitCast(d64, rhs);
        accu = hn::Add(hn::PopulationCount(hn::Xor(lhs_u64, rhs_u64)), accu);
    };

    using MyKernel = HwyReduceKernel<UsesNAccumulators<4>, UnrolledBy<4>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d8, d64, lhs_u8, rhs_u8, sz, hn::Zero(d64), kernel_fn, VecAdd(), LaneReduceSum());
}

// Multiply i8*i8 with the result widened to i16. Widen the intermediate i16 results to i32 and accumulate.
// Depending on the implementation, the intermediate i16 widening step may be transparently done.
HWY_INLINE
int32_t mul_add_i8_to_i32(const int8_t* HWY_RESTRICT a,
                          const int8_t* HWY_RESTRICT b,
                          const size_t sz) noexcept
{
    const hn::ScalableTag<int8_t> d8;
#if HWY_TARGET != HWY_NEON
    const hn::Repartition<int32_t, decltype(d8)> d32;
    const auto kernel_fn = [d32](auto lhs_i8, auto rhs_i8, auto& accu) VESPA_HWY_LAMBDA {
        accu = hn::SumOfMulQuadAccumulate(d32, lhs_i8, rhs_i8, accu);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), kernel_fn, VecAdd(), LaneReduceSum());
#else // HWY_NEON
    // The `SumOfMulQuadAccumulate` op has suboptimal codegen for the baseline NEON target since
    // it has to "emulate" the SDOT i8*i8 -> i16+i16 -> i32 instruction semantics without having
    // access to any more explicit input/output accumulators, i.e. it can't readily use the
    // `ReorderWidenMulAccumulate` operation since that takes in an extra output accumulator vector.
    // We work around this by having a NEON-specific implementation that does not use this emulation,
    // but instead runs 4x per-kernel accumulators in parallel and using high/low i8->i16 promotion
    // before doing a fused mul+add of pairwise i16->i32 (this is similar to how we do i8 Euclidean
    // distance computations).
    // This brings the performance slightly beyond what the compiler auto-vectorizer is capable of
    // conjuring up, since the auto-vectorizer does not seem to generate _fused_ mul+adds in this
    // case (on GCC 14 at least).
    const hn::Repartition<int16_t, decltype(d8)>  d16;
    const hn::Repartition<int32_t, decltype(d16)> d32;

    const auto kernel_fn = [d16, d32](auto lhs_i8, auto rhs_i8, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_HWY_LAMBDA {
        const auto lhs_i16_lo = hn::PromoteLowerTo(d16, lhs_i8);
        const auto lhs_i16_hi = hn::PromoteUpperTo(d16, lhs_i8);
        const auto rhs_i16_lo = hn::PromoteLowerTo(d16, rhs_i8);
        const auto rhs_i16_hi = hn::PromoteUpperTo(d16, rhs_i8);
        acc0 = hn::ReorderWidenMulAccumulate(d32, lhs_i16_lo, rhs_i16_lo, acc0, acc1);
        acc2 = hn::ReorderWidenMulAccumulate(d32, lhs_i16_hi, rhs_i16_hi, acc2, acc3);
    };

    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<2>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), kernel_fn, VecAdd(), LaneReduceSum());
#endif // HWY_NEON
}

HWY_INLINE
int64_t my_hwy_dot_int8(const int8_t* HWY_RESTRICT a,
                        const int8_t* HWY_RESTRICT b,
                        const size_t sz) noexcept
{
    // If we cannot possibly overflow intermediate i32 accumulators we can directly
    // compute the dot product without requiring any chunking. Max chunk size is defined
    // by the number of worst-case sums of i8 multiplications (-128**2) that can fit
    // into a single i32 accumulator.
    constexpr size_t max_n_per_chunk = INT32_MAX / (INT8_MIN*INT8_MIN);
    return compute_chunked_sum<max_n_per_chunk, int64_t>(mul_add_i8_to_i32, a, b, sz);
}

// Only properly efficient with native widening fp16 fmadd support (aarch64 FP16+FHM or SVE2)
HWY_INLINE
float mul_add_fp8_e5m2_to_f32_via_fp16(const uint8_t* HWY_RESTRICT a,
                                       const uint8_t* HWY_RESTRICT b,
                                       const size_t sz) noexcept
{
    const hn::ScalableTag<uint8_t>                        du8;
    const hn::Repartition<uint16_t,       decltype(du8)>  du16;
    const hn::Repartition<hwy::float16_t, decltype(du8)>  df16;
    const hn::Repartition<hwy::float32_t, decltype(df16)> df32;
    using VF16 = hn::VFromD<decltype(df16)>;
    // FP8E5M2 is to f16 what bf16 is to f32, i.e. the sliced and diced upper half
    // representation. This means we can zero-extend to f16 and then use native CPU
    // support for f16->f32 fmadd ops.
    const auto kernel_fn = [du16, df16, df32](auto lhs_i8, auto rhs_i8, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_HWY_LAMBDA {
        const VF16 lhs_f16_lo = hn::BitCast(df16, hn::ShiftLeft<8>(ReorderPromoteFirstTo(du16, lhs_i8)));
        const VF16 lhs_f16_hi = hn::BitCast(df16, hn::ShiftLeft<8>(ReorderPromoteSecondTo(du16, lhs_i8)));
        const VF16 rhs_f16_lo = hn::BitCast(df16, hn::ShiftLeft<8>(ReorderPromoteFirstTo(du16, rhs_i8)));
        const VF16 rhs_f16_hi = hn::BitCast(df16, hn::ShiftLeft<8>(ReorderPromoteSecondTo(du16, rhs_i8)));
        acc0 = MyReorderWidenMulAccumulate(df32, lhs_f16_lo, rhs_f16_lo, acc0, acc1);
        acc2 = MyReorderWidenMulAccumulate(df32, lhs_f16_hi, rhs_f16_hi, acc2, acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<2>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(du8, df32, a, b, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

#if 0
#define DEBUG_PRINT_LANES8(d, name, v) hn::Print(d, name, v, 0, 8)
#define DEBUG_PRINT_LANES16(d, name, v) hn::Print(d, name, v, 0, 16)
#else
#define DEBUG_PRINT_LANES8(d, name, v)
#define DEBUG_PRINT_LANES16(d, name, v)
#endif

#if VESPA_HWY_HAS_FP16_ARITH

template <typename D, HWY_IF_F16_D(D)>
HWY_API
hn::VFromD<D> promote_fp8_e4m3_to(D df16, hn::VFromD<hn::Rebind<uint8_t, D>> v) {
    const hn::RebindToUnsigned<decltype(df16)> du16;
    using VU16 = hn::VFromD<decltype(du16)>;

    const VU16 as_u16 = hn::PromoteTo(du16, v);
    DEBUG_PRINT_LANES8(du16, "as_u16", as_u16);
    const VU16 sign = hn::ShiftRight<7>(as_u16);
    const VU16 mantissa_fp8 = hn::And(as_u16, hn::Set(du16, 0x7));
    const VU16 exp_fp8 = hn::And(hn::ShiftRight<3>(as_u16), hn::Set(du16, 0xF));
    const auto is_fp8_subnorm = hn::Eq(exp_fp8, hn::Zero(du16));
    DEBUG_PRINT_LANES8(du16, "is_fp8_subnorm", hn::VecFromMask(du16, is_fp8_subnorm));
    const auto is_fp8_nan = hn::Eq(hn::ShiftLeft<9>(as_u16), hn::Set(du16, 0xFE00)); // ignore sign bit
    // We normalize the fp8 subnormal by expressing it as fp16 (where all e4m3 subnormals
    // are covered by the normalized fp range) and doing a "magic" floating-point subtraction.
    // This subtraction will as part of IEEE 754 floating point semantics implicitly:
    //
    //  1. _Denormalize_ the operands by lining up the exponents. The smallest number's mantissa
    //     will be right shifted by a number of bits equal to the difference in the two (biased)
    //     exponents, simultaneously adjusting the exponent value up correspondingly.
    //  2. _Normalize_ the subtraction result so that its mantissa has a leading 1. This is the
    //     "inverse" of de-normalization where the mantissa is shifted left until this is
    //     satisfied, simultaneously adjusting the exponent value _down_ correspondingly.
    //
    // The magic subtraction operand value is chosen so that the subtraction cancels itself out
    // (i.e. as-if step 1 is a no-op) and for step 2 so that the normalization adjusts the other
    // exponent down to an exponent that exactly represents the fp8 subnormal as an fp16 _normal_
    // value.
    //
    // The smallest E4M3 subnormal is +/- 2^-9. The fp16 exponent bias is 15, so the biased
    // exponent value that represents this value (as a normal) in fp16 is 15-9=6.
    //
    // Example for the smallest E4M3 subnormal (exp=0000, mantissa=001):
    //
    // Let M be the magic exponent value that we OR with our subnormal E4M3 mantissa to build
    // a "raw" fp16 value. Reminder: fp16 has 10 mantissa bits.
    //
    // Logical normalization steps for adjusting mantissa to have a leading 1:
    //     exp    mantissa
    //  0:  M     0000000001 (initial state)
    //  1:  M-1   0000000010
    //  2:  M-2   0000000100
    //  ...
    // 10: M-10   0000000000
    //
    // We want M-10=6 (i.e. biased exp for 2^-9), so M=6+10 = 16
    //
    // This is directly inspired by Fabian Giesen's fast fp16 -> fp32 conversion routine
    // (see https://fgiesen.wordpress.com/2012/03/28/half-to-float-done-quic/), but adapted
    // to fp8 -> fp16 and using only fp16 arithmetic. This requires the use of our own
    // subtraction ops since Highway doesn't currently expose native fp16 arithmetic
    // (only widening conversion is currently supported). The blog post also does not go into
    // any details of how the magic number works, hence the above "reverse-engineered" wall
    // of text exposition.
    const VU16 magic = hn::Set(du16, 16 << 10);
    const VU16 subnorm_fixup = hn::BitCast(du16, MySub(hn::BitCast(df16, hn::Or(magic, mantissa_fp8)), hn::BitCast(df16, magic)));
    DEBUG_PRINT_LANES8(du16, "magic", magic);
    DEBUG_PRINT_LANES8(df16, "magic_fp16", hn::BitCast(df16, magic));
    DEBUG_PRINT_LANES8(df16, "mangled", hn::BitCast(df16, hn::Or(magic, mantissa_fp8)));
    DEBUG_PRINT_LANES8(df16, "subnorm_fixup", MySub(hn::BitCast(df16, hn::Or(magic, mantissa_fp8)), hn::BitCast(df16, magic)));
    DEBUG_PRINT_LANES8(du16, "subnorm_fixup_bits", hn::BitCast(du16, subnorm_fixup));
    const VU16 mantissa_fp16 = hn::ShiftLeft<10 - 3>(mantissa_fp8);
    const VU16 exp_fp16 = hn::Add(exp_fp8, hn::Set(du16, 15 - 7));
    const VU16 normal = hn::Or(hn::ShiftLeft<10>(exp_fp16), mantissa_fp16);
    const VU16 bits_finite = hn::IfThenElse(is_fp8_subnorm, subnorm_fixup, normal);
    // Note that our NaN has a single (quiet) bit set to bitwise match the f32->f16 conversion from the fp8 LUT
    const VU16 bits_fp16 = hn::IfThenElse(is_fp8_nan, hn::Set(du16, 0b0111'1110'0000'0000), bits_finite);
    DEBUG_PRINT_LANES8(df16, "bits_fp16", hn::BitCast(df16, bits_fp16));
    return hn::BitCast(df16, hn::Or(hn::ShiftLeft<15>(sign), bits_fp16));
}

#endif // VESPA_HWY_HAS_FP16_ARITH

// TODO split out as func that outputs u8 pair <MSBs sans sign, sign> instead? "decompose_to_fp16_parts?"
// This is almost 2x the speed of first promoting to 16 bits and doing most ops in that width
template <typename D, HWY_IF_U8_D(D)>
HWY_API
void promote_fp8e4m3fn(D du8, hn::VFromD<D> v,
                       hn::VFromD<hn::Repartition<hwy::float16_t, D>>& f16_lo_out,
                       hn::VFromD<hn::Repartition<hwy::float16_t, D>>& f16_hi_out) noexcept
{
    const hn::Repartition<uint16_t, decltype(du8)> du16;
    const hn::Repartition<hwy::float16_t, decltype(du8)> df16;
    using VU8  = hn::VFromD<decltype(du8)>;
    using VU16 = hn::VFromD<decltype(du16)>;
    // E4M3 exponent bias is 7
    // No infinity
    // +/-NaN is S.1111.111
    // Subnormals are S.0000.{000-111}

    // For our next trick, convert signed E4M3 to an "unsigned" E5M3 (_not_ E5M2)
    // which very conveniently fits within an 8-bit lane and lets us repurpose the
    // now unused sign bit as an extra exponent bit. We track the sign bit separately,
    // since it can always be inserted as the MSB of a logical _signed_ E5M3 (its
    // unsigned representation shifted right by 1) and be correct for all fp cases
    // (zero, normal, subnormal, NaN). This resulting 9-bit floating point value is
    // a prefix of the full f16 value, just as bf16 would be for f32.
    const VU8 sign_only     = hn::And(v, hn::Set(du8, 0b1000'0000));
    const VU8 v_no_sign     = hn::And(v, hn::Set(du8, 0b0111'1111));
    const VU8 lo_4_bits     = hn::And(v, hn::Set(du8, 0b0000'1111));
    const VU8 mantissa_only = hn::And(v, hn::Set(du8, 0b0000'0111));
    const VU8 exp_only      = hn::And(v_no_sign, hn::Set(du8, 0b0111'1000));
    const VU8 adj_exp       = hn::Add(exp_only, hn::Set(du8, (15 - 7) << 3)); // "pre-shifted", as-if ((exp_only >> 3) + 8) << 3
    // "special" is +/- Zero, NaN or a subnormal. E4M3 has only a single NaN value, which is quiet.
    const auto is_special = hn::Or(hn::Eq(exp_only, hn::Zero(du8)), hn::Eq(v_no_sign, hn::Set(du8, 0b0111'1111)));
    // Important: LUT values are the top MSBs _offset 1_, i.e. without the sign bit
    const VU8 special_lut = hn::Dup128VecFromValues(du8,
        // Zero, followed by 7 subnormals
        0b00000000, 0b00110000, 0b00111000, 0b00111100, 0b01000000, 0b01000010, 0b01000100, 0b01000110,
        // All lookups with 4th bit set is a NaN (since all subnormals have an exponent of zero).
        // Map all of these to the same fp16 qNaN value.
        0b11111100, 0b11111100, 0b11111100, 0b11111100, 0b11111100, 0b11111100, 0b11111100, 0b11111100
    );
    const VU8 msb_no_sign = hn::IfThenElse(is_special,
                                           hn::TableLookupBytes(special_lut, lo_4_bits),
                                           hn::Or(adj_exp, mantissa_only));

    // Move up _almost_ to MSB, leaving room for the sign bit
    const VU16 unsigned_shifted_lo = hn::ShiftLeft<7>(hn::PromoteLowerTo(du16, msb_no_sign));
    f16_lo_out = hn::BitCast(df16, hn::Or(hn::ShiftLeft<8>(hn::PromoteLowerTo(du16, sign_only)), unsigned_shifted_lo));

    const VU16 unsigned_shifted_hi = hn::ShiftLeft<7>(hn::PromoteUpperTo(du16, msb_no_sign));
    f16_hi_out = hn::BitCast(df16, hn::Or(hn::ShiftLeft<8>(hn::PromoteUpperTo(du16, sign_only)), unsigned_shifted_hi));
}

template <typename D, HWY_IF_U8_D(D)>
HWY_API
void promote_fp8e4m3fn(D du8, hn::VFromD<D> v,
                       hn::VFromD<hn::Repartition<hwy::bfloat16_t, D>>& bf16_lo_out,
                       hn::VFromD<hn::Repartition<hwy::bfloat16_t, D>>& bf16_hi_out) noexcept
{
    const hn::Repartition<uint16_t, decltype(du8)> du16;
    const hn::Repartition<hwy::bfloat16_t, D> dbf16;
    using VU8  = hn::VFromD<decltype(du8)>;
    using VU16 = hn::VFromD<decltype(du16)>;
    // E4M3 exponent bias is 7
    // No infinity
    // +/-NaN is S.1111.111
    // Subnormals are S.0000.{000-111}
    const VU8 sign_only     = hn::And(v, hn::Set(du8, 0b1000'0000));
    const VU8 v_no_sign     = hn::And(v, hn::Set(du8, 0b0111'1111));
    const VU8 lo_4_bits     = hn::And(v, hn::Set(du8, 0b0000'1111));
    const VU8 mantissa_only = hn::And(v, hn::Set(du8, 0b0000'0111));
    const VU8 exp_only      = hn::And(hn::ShiftRight<3>(v), hn::Set(du8, 0b0000'1111));
    const VU8 adj_exp       = hn::Add(exp_only, hn::Set(du8, 127 - 7)); // f32 exponent bias adjust
    // "special" is +/- Zero, NaN or a subnormal. E4M3 has only a single NaN value, which is quiet.
    const auto is_special = hn::Or(hn::Eq(exp_only, hn::Zero(du8)), hn::Eq(v_no_sign, hn::Set(du8, 0b0111'1111)));
    // Important: LUT values are the top MSBs _offset 1_, i.e. without the sign bit
    const VU8 special_exp_lut = hn::Dup128VecFromValues(du8,
        // Zero, followed by 7 subnormals
        0b00000000, 0b01110110, 0b01110111, 0b01110111, 0b01111000, 0b01111000, 0b01111000, 0b01111000,
        // All lookups with 4th bit set is a NaN (since all subnormals have an exponent of zero).
        // qNaN bit follows in MSB of special mantissa LUT
        0b11111111, 0b11111111, 0b11111111, 0b11111111, 0b11111111, 0b11111111, 0b11111111, 0b11111111
    );
    const VU8 special_mantissa_lut = hn::Dup128VecFromValues(du8,
        // Zero, followed by 7 subnormals
        0b00000000, 0b00000000, 0b00000000, 0b01000000, 0b00000000, 0b00100000, 0b01000000, 0b01100000,
        // qNaN
        0b01000000, 0b01000000, 0b01000000, 0b01000000, 0b01000000, 0b01000000, 0b01000000, 0b01000000
    );
    const VU8 f32_exp = hn::IfThenElse(is_special, hn::TableLookupBytes(special_exp_lut, lo_4_bits), adj_exp);
    // LUT mantissa bits are already "left aligned"; must shift the extracted mantissa bits up similarly
    const VU8 f32_mantissa = hn::IfThenElse(is_special,
                                            hn::TableLookupBytes(special_mantissa_lut, lo_4_bits),
                                            hn::ShiftLeft<4>(mantissa_only));

    // TODO interleave exp/mantissa lanes if possible instead of promoting and shifting
    // Move up _almost_ to MSB, leaving room for the sign bit
    const VU16 unsigned_shifted_lo = hn::Or(hn::ShiftLeft<7>(hn::PromoteLowerTo(du16, f32_exp)),
                                            hn::PromoteLowerTo(du16, f32_mantissa));
    bf16_lo_out = hn::BitCast(dbf16, hn::Or(hn::ShiftLeft<8>(hn::PromoteLowerTo(du16, sign_only)), unsigned_shifted_lo));

    const VU16 unsigned_shifted_hi = hn::Or(hn::ShiftLeft<7>(hn::PromoteUpperTo(du16, f32_exp)),
                                            hn::PromoteUpperTo(du16, f32_mantissa));
    bf16_hi_out = hn::BitCast(dbf16, hn::Or(hn::ShiftLeft<8>(hn::PromoteUpperTo(du16, sign_only)), unsigned_shifted_hi));
}

template <typename ViaT>
HWY_API
float mul_add_fp8_e4m3fn_to_f32_via(const uint8_t* HWY_RESTRICT a,
                                    const uint8_t* HWY_RESTRICT b,
                                    const size_t sz) noexcept {
    const hn::ScalableTag<uint8_t> du8;
    const hn::Repartition<ViaT, decltype(du8)> dt16;
    const hn::Repartition<float, decltype(dt16)> df32;

    auto kernel_fn = [du8, df32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_HWY_LAMBDA {
        hn::VFromD<decltype(dt16)> lhs_t16_lo, lhs_t16_hi;
        promote_fp8e4m3fn(du8, lhs, lhs_t16_lo, lhs_t16_hi);

        hn::VFromD<decltype(dt16)> rhs_t16_lo, rhs_t16_hi;
        promote_fp8e4m3fn(du8, rhs, rhs_t16_lo, rhs_t16_hi);

        acc0 = MyReorderWidenMulAccumulate(df32, lhs_t16_lo, rhs_t16_lo, acc0, acc1);
        acc2 = MyReorderWidenMulAccumulate(df32, lhs_t16_hi, rhs_t16_hi, acc2, acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<2>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(du8, df32, a, b, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

[[maybe_unused]]
HWY_INLINE
float mul_add_fp8_e4m3fn_to_f32_via_fp16(const uint8_t* HWY_RESTRICT a,
                                         const uint8_t* HWY_RESTRICT b,
                                         const size_t sz) noexcept {
    return mul_add_fp8_e4m3fn_to_f32_via<hwy::float16_t>(a, b, sz);
}

[[maybe_unused]]
HWY_INLINE
float mul_add_fp8_e4m3fn_to_f32_via_bf16(const uint8_t* HWY_RESTRICT a,
                                         const uint8_t* HWY_RESTRICT b,
                                         const size_t sz) noexcept {
    return mul_add_fp8_e4m3fn_to_f32_via<hwy::bfloat16_t>(a, b, sz);
}

#if VESPA_HWY_HAS_FP16_ARITH

HWY_API
float mul_add_fp8_e4m3fn_to_f32_via_fp16_v2(const uint8_t* HWY_RESTRICT a,
                                            const uint8_t* HWY_RESTRICT b,
                                            const size_t sz) noexcept {
    const hn::ScalableTag<uint8_t> du8;
    const hn::Repartition<hwy::float16_t, decltype(du8)> df16;
    const hn::Repartition<float, decltype(df16)> df32;

    auto kernel_fn = [du8, df16, df32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_HWY_LAMBDA {
        using VF16 = hn::VFromD<decltype(df16)>;
        // TODO SVE friendly
        VF16 lhs_f16_lo = promote_fp8_e4m3_to(df16, hn::LowerHalf(lhs));
        VF16 rhs_f16_lo = promote_fp8_e4m3_to(df16, hn::LowerHalf(rhs));
        acc0 = MyReorderWidenMulAccumulate(df32, lhs_f16_lo, rhs_f16_lo, acc0, acc1);
        VF16 lhs_f16_hi = promote_fp8_e4m3_to(df16, hn::UpperHalf(du8, lhs));
        VF16 rhs_f16_hi = promote_fp8_e4m3_to(df16, hn::UpperHalf(du8, rhs));
        acc2 = MyReorderWidenMulAccumulate(df32, lhs_f16_hi, rhs_f16_hi, acc2, acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<2>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(du8, df32, a, b, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

#endif // #if VESPA_HWY_HAS_FP16_ARITH

template <typename D, HWY_IF_U8_D(D)>
HWY_API
void reorder_promote_fp4e2m1(D du8, hn::VFromD<D> v, // 4 MSBs must be zero in all lanes
                             hn::VFromD<hn::Repartition<hwy::float16_t, D>>& f16_lo_out,
                             hn::VFromD<hn::Repartition<hwy::float16_t, D>>& f16_hi_out) noexcept
{
    const auto fp16_msb_lut = hn::Dup128VecFromValues(du8,
        0x00, 0x38, 0x3c, 0x3e, 0x40, 0x42, 0x44, 0x46,
        0x80, 0xb8, 0xbc, 0xbe, 0xc0, 0xc2, 0xc4, 0xc6
    );
    const auto fp16_msb = hn::TableLookupBytes(fp16_msb_lut, v);

    const hn::Repartition<uint16_t, D> du16;
    const hn::Repartition<hwy::float16_t, D> df16;
    // TODO benchmark with/without reordering
    f16_lo_out = hn::BitCast(df16, hn::ShiftLeft<8>(ReorderPromoteFirstTo(du16, fp16_msb)));
    f16_hi_out = hn::BitCast(df16, hn::ShiftLeft<8>(ReorderPromoteSecondTo(du16, fp16_msb)));
    //f16_lo_out = hn::BitCast(df16, hn::ShiftLeft<8>(hn::PromoteLowerTo(du16, fp16_msb)));
    //f16_hi_out = hn::BitCast(df16, hn::ShiftLeft<8>(hn::PromoteUpperTo(du16, fp16_msb)));
}

template <typename D, HWY_IF_U8_D(D)>
HWY_API
void reorder_promote_fp4e2m1(D du8, hn::VFromD<D> v, // 4 MSBs must be zero in all lanes
                             hn::VFromD<hn::Repartition<hwy::bfloat16_t, D>>& bf16_lo_out,
                             hn::VFromD<hn::Repartition<hwy::bfloat16_t, D>>& bf16_hi_out) noexcept
{
    using VU8 = hn::VFromD<decltype(du8)>;
    // These LUTs map 1-1 from the 4 bit float to its 2-byte BFloat16 representation.
    const auto bf16_msb_lut = hn::Dup128VecFromValues(du8,
        0x00, 0x3f, 0x3f, 0x3f, 0x40, 0x40, 0x40, 0x40,
        0x80, 0xbf, 0xbf, 0xbf, 0xc0, 0xc0, 0xc0, 0xc0
    );
    const auto bf16_lsb_lut = hn::Dup128VecFromValues(du8,
        0x00, 0x00, 0x80, 0xc0, 0x00, 0x40, 0x80, 0xc0,
        0x00, 0x00, 0x80, 0xc0, 0x00, 0x40, 0x80, 0xc0
    );
    const VU8 bf16_msb = hn::TableLookupBytes(bf16_msb_lut, v);
    const VU8 bf16_lsb = hn::TableLookupBytes(bf16_lsb_lut, v);
    static_assert(std::endian::native == std::endian::little,
                  "Lane interleaving currently only defined for little endian");
    // Note: MSB/LSB order is switched since we end up reinterpreting each pair of
    // u8 lanes as 1x BF16 lane, and endianness directly affects this.
    const VU8 fused_odd  = hn::InterleaveOdd(du8, bf16_lsb, bf16_msb);
    const VU8 fused_even = hn::InterleaveEven(du8, bf16_lsb, bf16_msb);

    const hn::Repartition<hwy::bfloat16_t, D> dbf16;
    bf16_lo_out = hn::BitCast(dbf16, fused_even);
    bf16_hi_out = hn::BitCast(dbf16, fused_odd);
}

// TODO scaling factors...!
// TODO dedupe

template <typename ViaT>
HWY_API
float mul_add_fp4_e2m1_to_f32_via(const uint8_t* HWY_RESTRICT a,
                                  const uint8_t* HWY_RESTRICT b,
                                  const size_t sz) noexcept {
    const hn::ScalableTag<uint8_t> du8;
    const hn::Repartition<ViaT, decltype(du8)> dt16;
    const hn::Repartition<float, decltype(dt16)> df32;

    auto kernel_fn = [du8, df32](auto lhs, auto rhs,
                                 auto& acc0, auto& acc1, auto& acc2, auto& acc3,
                                 auto& acc4, auto& acc5, auto& acc6, auto& acc7) VESPA_HWY_LAMBDA
    {
        const auto lhs_lo4 = hn::And(lhs, hn::Set(du8, 0x0f));
        const auto lhs_hi4 = hn::ShiftRight<4>(lhs);
        const auto rhs_lo4 = hn::And(rhs, hn::Set(du8, 0x0f));
        const auto rhs_hi4 = hn::ShiftRight<4>(rhs);

        // TODO have a native fp8 version once supported
        hn::VFromD<decltype(dt16)> lhs_t16_lo, lhs_t16_hi;
        hn::VFromD<decltype(dt16)> rhs_t16_lo, rhs_t16_hi;

        reorder_promote_fp4e2m1(du8, lhs_lo4, lhs_t16_lo, lhs_t16_hi);
        reorder_promote_fp4e2m1(du8, rhs_lo4, rhs_t16_lo, rhs_t16_hi);
        acc0 = MyReorderWidenMulAccumulate(df32, lhs_t16_lo, rhs_t16_lo, acc0, acc1);
        acc2 = MyReorderWidenMulAccumulate(df32, lhs_t16_hi, rhs_t16_hi, acc2, acc3);

        reorder_promote_fp4e2m1(du8, lhs_hi4, lhs_t16_lo, lhs_t16_hi);
        reorder_promote_fp4e2m1(du8, rhs_hi4, rhs_t16_lo, rhs_t16_hi);
        acc4 = MyReorderWidenMulAccumulate(df32, lhs_t16_lo, rhs_t16_lo, acc4, acc5);
        acc6 = MyReorderWidenMulAccumulate(df32, lhs_t16_hi, rhs_t16_hi, acc6, acc7);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<2>, HasAccumulatorArity<8>>;
    return MyKernel::pairwise(du8, df32, a, b, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

[[maybe_unused]]
HWY_INLINE
float mul_add_fp4_e2m1_to_f32_via_bf16(const uint8_t* HWY_RESTRICT a,
                                       const uint8_t* HWY_RESTRICT b,
                                       const size_t sz) noexcept {
    return mul_add_fp4_e2m1_to_f32_via<hwy::bfloat16_t>(a, b, sz);
}

[[maybe_unused]]
HWY_INLINE
float mul_add_fp4_e2m1_to_f32_via_fp16(const uint8_t* HWY_RESTRICT a,
                                       const uint8_t* HWY_RESTRICT b,
                                       const size_t sz) noexcept {
    return mul_add_fp4_e2m1_to_f32_via<hwy::float16_t>(a, b, sz);
}

[[maybe_unused]]
HWY_API
float mul_add_fp8_e4m3fn_to_f32_gather_lut(const uint8_t* HWY_RESTRICT a,
                                           const uint8_t* HWY_RESTRICT b,
                                           const size_t sz) noexcept {
    const hn::ScalableTag<uint8_t> du8;
    const hn::Repartition<uint16_t, decltype(du8)>  du16;
    const hn::Repartition<uint32_t, decltype(du16)> du32;
    const hn::RebindToSigned<decltype(du32)>        di32;
    const hn::RebindToFloat<decltype(du32)>         df32;

    using VU16 = hn::VFromD<decltype(du16)>;
    using VI32 = hn::VFromD<decltype(di32)>;

    // FIXME avoid type aliasing...
    const auto* const fp8_lut = reinterpret_cast<const float*>(fp8_e4m3fn_f32_bits_lut);

    auto kernel_fn = [du16, di32, df32, fp8_lut](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_HWY_LAMBDA {
        const VU16 lhs_u16_0 = ReorderPromoteFirstTo(du16, lhs);
        const VU16 rhs_u16_0 = ReorderPromoteFirstTo(du16, rhs);
        const VU16 lhs_u16_1 = ReorderPromoteSecondTo(du16, lhs);
        const VU16 rhs_u16_1 = ReorderPromoteSecondTo(du16, rhs);

        // Important: the LUT gather ops work fine with our implicit zeroing
        // of OOB vector elements since the FP8 bit pattern of all zeroes is
        // also the zero float, so they will be as-if no-ops.

        const VI32 lhs_i32_0_0 = ReorderPromoteFirstTo(di32, lhs_u16_0);
        const VI32 rhs_i32_0_0 = ReorderPromoteFirstTo(di32, rhs_u16_0);
        acc0 = hn::MulAdd(hn::GatherIndex(df32, fp8_lut, lhs_i32_0_0),
                          hn::GatherIndex(df32, fp8_lut, rhs_i32_0_0), acc0);

        const VI32 lhs_i32_0_1 = ReorderPromoteSecondTo(di32, lhs_u16_0);
        const VI32 rhs_i32_0_1 = ReorderPromoteSecondTo(di32, rhs_u16_0);
        acc1 = hn::MulAdd(hn::GatherIndex(df32, fp8_lut, lhs_i32_0_1),
                          hn::GatherIndex(df32, fp8_lut, rhs_i32_0_1), acc1);

        const VI32 lhs_i32_1_0 = ReorderPromoteFirstTo(di32, lhs_u16_1);
        const VI32 rhs_i32_1_0 = ReorderPromoteFirstTo(di32, rhs_u16_1);
        acc2 = hn::MulAdd(hn::GatherIndex(df32, fp8_lut, lhs_i32_1_0),
                          hn::GatherIndex(df32, fp8_lut, rhs_i32_1_0), acc2);

        const VI32 lhs_i32_1_1 = ReorderPromoteSecondTo(di32, lhs_u16_1);
        const VI32 rhs_i32_1_1 = ReorderPromoteSecondTo(di32, rhs_u16_1);
        acc3 = hn::MulAdd(hn::GatherIndex(df32, fp8_lut, lhs_i32_1_1),
                          hn::GatherIndex(df32, fp8_lut, rhs_i32_1_1), acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<2>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(du8, df32, a, b, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

HWY_INLINE
const char* my_hwy_target_name() noexcept {
    return hwy::TargetName(HWY_TARGET);
}

[[nodiscard]] uint16_t vector_byte_size() noexcept {
    const hn::ScalableTag<int8_t> d8; // Widest possible runtime vector
    return static_cast<uint16_t>(hn::Lanes(d8)); // Presumably no vectors with more than 524K bits for some time...
}

int64_t my_dot_product_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return my_hwy_dot_int8(a, b, sz);
}
float my_dot_product_bf16(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    return my_hwy_dot_bf16(a, b, sz);
}
float my_dot_product_f32(const float* a, const float* b, size_t sz) noexcept {
    return my_hwy_dot_float(a, b, sz);
}
double my_dot_product_f64(const double* a, const double* b, size_t sz) noexcept {
    return my_hwy_dot_double(a, b, sz);
}
HWY_NOINLINE
float my_dot_product_f8_e4m3fn(const uint8_t* a, const uint8_t* b, size_t sz) noexcept {
#if VESPA_HWY_HAS_FP16_ARITH && 0 /*TODO*/
    // FIXME this is slower than all 8-bit options
    return mul_add_fp8_e4m3fn_to_f32_via_fp16_v2(a, b, sz);
#elif 0 // TODO, ~0.5x speed of fp16 impl
    return mul_add_fp8_e4m3fn_to_f32_gather_lut(a, b, sz);
#else
    // TODO figure out why BF16 intermediate is slower on NEON_BF16 even when no fp16 FMA is available...!
    //return mul_add_fp8_e4m3fn_to_f32_via_bf16(a, b, sz);
    return mul_add_fp8_e4m3fn_to_f32_via_fp16(a, b, sz);
#endif
}
HWY_NOINLINE
float my_dot_product_f8_e5m2(const uint8_t* a, const uint8_t* b, size_t sz) noexcept {
    return mul_add_fp8_e5m2_to_f32_via_fp16(a, b, sz);
}
HWY_NOINLINE
float my_dot_product_f4_e2m1(const uint8_t* a, const uint8_t* b, size_t sz) noexcept {
    // TODO benchmark with both on representative platforms
#if defined(HWY_NATIVE_REORDER_WIDEN_MUL_ACC_BF16)
    return mul_add_fp4_e2m1_to_f32_via_bf16(a, b, sz);
#else
    return mul_add_fp4_e2m1_to_f32_via_fp16(a, b, sz);
#endif
}
float my_dot_product_micro_float(const uint8_t* a, const uint8_t* b, size_t sz, MicroFloatKind kind) noexcept {
    switch (kind) {
    case MicroFloatKind::FP8_E4M2FN: return my_dot_product_f8_e4m3fn(a, b, sz);
    case MicroFloatKind::FP8_E5M2:   return my_dot_product_f8_e5m2(a, b, sz);
    case MicroFloatKind::FP4_E2M1:   return my_dot_product_f4_e2m1(a, b, sz);
    }
    abort();
}
double my_squared_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return my_hwy_square_euclidean_distance_int8(a, b, sz);
}
double my_squared_euclidean_distance_bf16(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    return my_hwy_square_euclidean_distance_bf16(a, b, sz);
}
double my_squared_euclidean_distance_f32(const float* a, const float* b, size_t sz) noexcept {
    return my_hwy_square_euclidean_distance(a, b, sz);
}
double my_squared_euclidean_distance_f64(const double* a, const double* b, size_t sz) noexcept {
    return my_hwy_square_euclidean_distance(a, b, sz);
}
size_t my_binary_hamming_distance(const void* lhs, const void* rhs, size_t sz) noexcept {
    return my_hwy_binary_hamming_distance(lhs, rhs, sz);
}
size_t my_population_count(const uint64_t* buf, size_t sz) noexcept {
    return my_hwy_popcount(buf, sz);
}
[[maybe_unused]]
float my_mul_add_fp8_e5m2_to_f32(const uint8_t* a, const uint8_t* b, const size_t sz) noexcept {
    return mul_add_fp8_e5m2_to_f32_via_fp16(a, b, sz);
}
TargetInfo my_target_info() noexcept {
    return {"Highway", my_hwy_target_name(), vector_byte_size()};
}

} // anon ns

#if VESPA_HWY_HAS_FP16_ARITH
void test_fp8_to_fp16(const uint8_t* HWY_RESTRICT buf, uint16_t* HWY_RESTRICT out, size_t n) {
    const hn::ScalableTag<uint8_t> du8;
    const hn::Repartition<hwy::float16_t, decltype(du8)> df16;
    const hn::RebindToUnsigned<decltype(df16)> du16;
    const size_t N = std::min(hn::Lanes(du8), n);
    const auto v = hn::LoadN(du8, buf, N);
    using VF16 = hn::VFromD<decltype(df16)>;
    VF16 as_f16 = promote_fp8_e4m3_to(df16, hn::LowerHalf(v));
    DEBUG_PRINT_LANES8(df16, "as_f16", as_f16);
    hn::StoreN(hn::BitCast(du16, as_f16), du16, out, N);
}
#endif

class HwyTargetAccelerator final : public PlatformGenericAccelerator {
public:
    ~HwyTargetAccelerator() override = default;

    TargetInfo target_info() const noexcept override {
        return my_target_info();
    }

    [[nodiscard]] static dispatch::FnTable build_fn_table() {
        using dispatch::FnTable;
        FnTable ft(my_target_info());
        ft.dot_product_i8   = my_dot_product_i8;
        ft.dot_product_bf16 = my_dot_product_bf16;
        ft.dot_product_f32  = my_dot_product_f32;
        ft.dot_product_f64  = my_dot_product_f64;
        ft.dot_product_micro_float = my_dot_product_micro_float;
        ft.squared_euclidean_distance_i8   = my_squared_euclidean_distance_i8;
        ft.squared_euclidean_distance_bf16 = my_squared_euclidean_distance_bf16;
        ft.squared_euclidean_distance_f32  = my_squared_euclidean_distance_f32;
        ft.squared_euclidean_distance_f64  = my_squared_euclidean_distance_f64;
        ft.binary_hamming_distance = my_binary_hamming_distance;
        ft.population_count = my_population_count;
#if HWY_TARGET == HWY_AVX2 || HWY_TARGET == HWY_AVX3
        // AVX2 and AVX3 do not have dedicated vector popcount instructions, so the Highway "emulation"
        // ends up being slower in practice than the baseline one using 4x pipelined POPCNT.
        ft.tag_fns_as_suboptimal({FnTable::FnId::BINARY_HAMMING_DISTANCE, FnTable::FnId::POPULATION_COUNT});
#endif
#if (HWY_TARGET & HWY_ALL_SVE) != 0
        // The SVE BFDOT instruction is not used by Highway for BF16 dot products due to a
        // different rounding mode than that of NEON.
        // Additionally, BF16 squared Euclidean distance is reduced on Axion and Graviton 4
        // SVE+SVE2 (but _not_ on Graviton 3 SVE... need auto-tuning on startup).
        ft.tag_fns_as_suboptimal({FnTable::FnId::DOT_PRODUCT_BF16, FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_BF16});
        // SVE (1st edition) does not have signed subtraction with widening, causing i8
        // Euclidean to be slower than under NEON. SVE2 does have this, but int8 operations
        // are still slightly slower for the SVEs. So tag as suboptimal for now.
        ft.tag_fns_as_suboptimal({FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_I8, FnTable::FnId::DOT_PRODUCT_I8});
        // f64 is a tiny bit slower for dot products on SVE/SVE2.
        ft.tag_fns_as_suboptimal({FnTable::FnId::DOT_PRODUCT_F64});
#if HWY_TARGET == HWY_SVE || HWY_TARGET == HWY_SVE_256
        // Squared Euclidean distance is slower for f64 on SVE/SVE_256 (Graviton 3)
        ft.tag_fns_as_suboptimal({FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F64});
#endif // HWY_TARGET == HWY_SVE || HWY_TARGET == HWY_SVE_256
#if HWY_TARGET != HWY_SVE2_128
        // f32/f64 dot products are slightly slower across the board on non-fixed width SVE/SVE2.
        // SVE2_128, however, is overall _faster_ for f32.
        ft.tag_fns_as_suboptimal({FnTable::FnId::DOT_PRODUCT_F32});
#endif // HWY_TARGET != HWY_SVE2_128
#endif // (HWY_TARGET & HWY_ALL_SVE) != 0
        return ft;
    }

    const dispatch::FnTable& fn_table() const override {
        static const dispatch::FnTable tbl = build_fn_table();
        return tbl;
    }

    [[nodiscard]] static std::unique_ptr<IAccelerated> create_instance() {
        return std::make_unique<HwyTargetAccelerator>();
    }
};

}  // namespace HWY_NAMESPACE
}  // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#if HWY_ONCE

namespace vespalib::hwaccelerated {

namespace {

#define VESPA_HWY_ADD_SUPPORTED_IMPL_VISITOR(hwy_target, hwy_ns) \
    if ((supported_targets & hwy_target) != 0) { \
        target_id_and_impl.emplace_back(hwy_target, hwy_ns::HwyTargetAccelerator::create_instance()); \
    }

std::vector<std::pair<uint64_t, std::unique_ptr<IAccelerated>>> create_supported_targets_with_impls() {
    // On x64 we require AVX2 as a baseline, so don't bother wasting time with SSSE3/SSE4.
    // Ideally we would not even build these targets. No effect on Aarch64.
    hwy::DisableTargets(HWY_SSSE3 | HWY_SSE4);
    const uint64_t supported_targets = hwy::SupportedTargets();

    std::vector<std::pair<uint64_t, std::unique_ptr<IAccelerated>>> target_id_and_impl;
    // Visits _compile-time_ supported targets in _alphabetical_ (i.e. not preferred) order.
    // Intersect these with the _run-time_ supported targets and keep track of their target IDs.
    // Since lower target IDs are considered more preferred, we then post-sort the list in
    // preference order.
    HWY_VISIT_TARGETS(VESPA_HWY_ADD_SUPPORTED_IMPL_VISITOR);
    std::ranges::sort(target_id_and_impl, [](const auto& lhs, const auto& rhs) noexcept {
        return lhs.first < rhs.first;
    });
    // We might get a duplicate output due to HWY_VISIT_TARGETS including the fallback static
    // target, which is likely equal to another target. Remove dupes to avoid having to deal
    // with this at a higher level.
    auto to_erase = std::ranges::unique(target_id_and_impl, [](const auto& lhs, const auto& rhs) noexcept {
        return lhs.first == rhs.first;
    });
    target_id_and_impl.erase(to_erase.begin(), to_erase.end());
    return target_id_and_impl;
}

// The "best" supported target will be the first element in the vector.
std::vector<std::unique_ptr<IAccelerated>> create_supported_targets_impl() {
    auto target_id_and_impl = create_supported_targets_with_impls();

    std::vector<std::unique_ptr<IAccelerated>> preferred_target_order;
    preferred_target_order.reserve(target_id_and_impl.size());
    for (auto& elem : target_id_and_impl) {
        preferred_target_order.emplace_back(std::move(elem.second));
    }
    // If Vespa is compiled for a prehistoric CPU (e.g. < AVX2), it's possible we don't
    // have any targets to return. In this case the caller must fall back to only using
    // auto-vectorized targets (likely only the baseline target used for compilation).
    return preferred_target_order;
}

} // anon ns

std::vector<std::unique_ptr<IAccelerated>> Highway::create_supported_targets() {
    return create_supported_targets_impl();
}

} // namespace vespalib::hwaccelerated

#endif // HWY_ONCE
