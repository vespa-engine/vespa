// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "highway.h"

#include "fn_table.h"
#include "platform_generic.h"
#include "target_info.h"
#include "unroller.h"

#include <vespa/check_require.h>

#include <hwy/base.h>

#include <algorithm>
#include <format>

// clang-format off

// This file will be recursively included into itself with different target
// compilation parameters. See the Highway docs.
#undef HWY_TARGET_INCLUDE
#define HWY_TARGET_INCLUDE "vespa/vespalib/hwaccelerated/highway.cpp"
#include <hwy/foreach_target.h>

#include "hwy_kernel-inl.h"
#include "hwy_aux_ops-inl.h"
#include <hwy/contrib/dot/dot-inl.h>

// clang-format on

#if 1
#include <hwy/print-inl.h>
#define DEBUG_PRINT_LANES4(d, name, v) hn::Print(d, name, v, 0, 4)
#define DEBUG_PRINT_LANES8(d, name, v) hn::Print(d, name, v, 0, 8)
#define DEBUG_PRINT_LANES16(d, name, v) hn::Print(d, name, v, 0, 16)
#define DEBUG_PRINT_LANES32(d, name, v) hn::Print(d, name, v, 0, 32)
#else
#define DEBUG_PRINT_LANES4(d, name, v)
#define DEBUG_PRINT_LANES8(d, name, v)
#define DEBUG_PRINT_LANES16(d, name, v)
#define DEBUG_PRINT_LANES32(d, name, v)
#endif

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;
namespace {
// Must always be undef'd to ensure we expand the correct HWY_ATTR per target.
#undef VESPA_HWY_LAMBDA
// Clang and GCC refuse to parse `noexcept HWY_ATTR` and `HWY_ATTR noexcept`,
// respectively. GCC seems to be the one that is technically correct(tm), but
// we still want Clang to compile, so hide the dirt under a macro carpet.
// Force lambda caller inlining to avoid GCC doing some wild codegen with vector
// register spilling to stack temporaries if it decides to break the lambda out
// as a separate logical function. For good measure, also ensure that we inline
// the lambda's _callees_. This mirrors the most prudent parts of HWY_API.
// `HWY_ATTR` is needed to ensure lambdas have the expected codegen target.
// See https://google.github.io/highway/en/master/faq.html#boilerplate
#if defined(__clang__)
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
    requires(hwy::IsFloat<T>() || hwy::IsSame<T, hwy::bfloat16_t>())
HWY_INLINE R my_hwy_dot_impl(const T* HWY_RESTRICT a, const T* HWY_RESTRICT b, const size_t sz) noexcept {
    const hn::ScalableTag<T> d;
    return hwy::ConvertScalarTo<R>(hn::Dot::Compute<0>(d, a, b, sz));
}

HWY_INLINE
float my_hwy_dot_float(const float* HWY_RESTRICT a, const float* HWY_RESTRICT b, const size_t sz) noexcept {
    return my_hwy_dot_impl(a, b, sz);
}

HWY_INLINE
double my_hwy_dot_double(const double* HWY_RESTRICT a, const double* HWY_RESTRICT b, const size_t sz) noexcept {
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
float my_hwy_dot_bf16(const BFloat16* HWY_RESTRICT a, const BFloat16* HWY_RESTRICT b, const size_t sz) noexcept {
    static_assert(sizeof(BFloat16) == sizeof(hwy::bfloat16_t));
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
        // f32 += bf16 * bf16
        acc0 = hn::ReorderWidenMulAccumulate(df32, lhs, rhs, acc0, acc1);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<4>, HasAccumulatorArity<2>>;
    return MyKernel::pairwise(dbf16, df32, a_bf16, b_bf16, sz, hn::Zero(df32), kernel_fn, VecAdd(), LaneReduceSum());
}

template <typename T>
    requires(hwy::IsFloat<T>())
HWY_INLINE double my_hwy_squared_euclidean_distance(const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                                    const size_t sz) noexcept {
    const hn::ScalableTag<T> d;

    const auto kernel_fn = [](auto lhs, auto rhs, auto& accu) VESPA_HWY_LAMBDA {
        const auto sub = hn::Sub(lhs, rhs);
        accu = hn::MulAdd(sub, sub, accu); // note: using fused multiply-add
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d, d, a, b, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
}

HWY_INLINE
double my_hwy_squared_euclidean_distance_bf16(const BFloat16* HWY_RESTRICT a, const BFloat16* HWY_RESTRICT b,
                                              const size_t sz) noexcept {
    static_assert(sizeof(BFloat16) == sizeof(hwy::bfloat16_t));
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
int32_t sub_mul_add_i8_to_i32(const int8_t* HWY_RESTRICT a, const int8_t* HWY_RESTRICT b, const size_t sz) {
    const hn::ScalableTag<int8_t>                 d8;
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
double my_hwy_square_euclidean_distance_int8(const int8_t* HWY_RESTRICT a, const int8_t* HWY_RESTRICT b,
                                             const size_t sz) noexcept {
    // If we cannot possibly overflow intermediate i32 accumulators we can directly
    // compute the distance without requiring any chunking. Max chunk size is defined
    // by the number of worst-case sums of +/-255**2 that can fit into an i32.
    // +/-255 is due to widening subtraction so that the max is 127 - (-128) or (-127) - 128.
    constexpr size_t max_n_per_chunk = INT32_MAX / (255 * 255);
    return compute_chunked_sum<max_n_per_chunk, double>(sub_mul_add_i8_to_i32, a, b, sz);
}

template <typename T>
    requires(hwy::IsFloat<T>())
HWY_INLINE T my_hwy_squared_euclidean_length(const T* a, const size_t sz) noexcept {
    const hn::ScalableTag<T> d;

    const auto kernel_fn = [](auto v, auto& accu) VESPA_HWY_LAMBDA {
        accu = hn::MulAdd(v, v, accu); // accu += v*v
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::elementwise(d, d, a, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
}

// Performance note: AVX2 and AVX3 do not have dedicated vector popcount instructions,
// so the Highway "emulation" ends up being slower in practice than the baseline one
// using 4x pipelined POPCNT.
HWY_INLINE
size_t my_hwy_popcount(const uint64_t* a, const size_t sz) noexcept {
    const hn::ScalableTag<uint64_t> d;
    const auto kernel_fn = [](auto v, auto& accu) VESPA_HWY_LAMBDA { accu = hn::Add(hn::PopulationCount(v), accu); };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::elementwise(d, d, a, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
}

// Performance note: will be slower on AVX2/AVX3; see `my_hwy_popcount`.
HWY_INLINE
size_t my_hwy_binary_hamming_distance(const void* HWY_RESTRICT untyped_lhs, const void* HWY_RESTRICT untyped_rhs,
                                      const size_t sz) noexcept {
    // Inputs may have arbitrary byte alignments, so we have to read with an 8-bit
    // type to ensure we don't violate any natural alignment requirements. The
    // `HwyReduceKernel` code uses unaligned vector loads, so this works fine and
    // with effectively the same performance regardless of input alignment.
    // We then do the vector equivalent of reinterpret_cast (which is well-defined)
    // to treat each set of 8x 8-bit lanes as 1x u64 lane before running 64-bit
    // xor -> popcount -> accumulate operations.
    const auto* lhs_u8 = static_cast<const uint8_t*>(untyped_lhs);
    const auto* rhs_u8 = static_cast<const uint8_t*>(untyped_rhs);

    const hn::ScalableTag<uint8_t>              d8;
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
int32_t mul_add_i8_to_i32(const int8_t* HWY_RESTRICT a, const int8_t* HWY_RESTRICT b, const size_t sz) noexcept {
    const hn::ScalableTag<int8_t> d8;
#if HWY_TARGET != HWY_NEON
    const hn::Repartition<int32_t, decltype(d8)> d32;

    const auto kernel_fn = [d32](auto lhs_i8, auto rhs_i8, auto& accu)
                               VESPA_HWY_LAMBDA { accu = hn::SumOfMulQuadAccumulate(d32, lhs_i8, rhs_i8, accu); };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), kernel_fn, VecAdd(), LaneReduceSum());
#else  // HWY_NEON
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

    const auto kernel_fn = [d16, d32](auto lhs_i8, auto rhs_i8, auto& acc0, auto& acc1, auto& acc2, auto& acc3)
                               VESPA_HWY_LAMBDA {
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
int64_t my_hwy_dot_int8(const int8_t* HWY_RESTRICT a, const int8_t* HWY_RESTRICT b, const size_t sz) noexcept {
    // If we cannot possibly overflow intermediate i32 accumulators we can directly
    // compute the dot product without requiring any chunking. Max chunk size is defined
    // by the number of worst-case sums of i8 multiplications (-128**2) that can fit
    // into a single i32 accumulator.
    constexpr size_t max_n_per_chunk = INT32_MAX / (INT8_MIN * INT8_MIN);
    return compute_chunked_sum<max_n_per_chunk, int64_t>(mul_add_i8_to_i32, a, b, sz);
}

template <typename T>
concept InvocationMeta = requires(T a) {
    { a.is_partial() } -> std::same_as<bool>;
    { a.remaining() } -> std::same_as<size_t>;
    { a.unroll_index() } -> std::same_as<size_t>;
};

template <size_t UnrollIdx>
struct BlockInvocation {
    [[nodiscard]] constexpr bool is_partial() const noexcept { return false; }
    [[nodiscard]] constexpr size_t remaining() const noexcept { return 0; }
    [[nodiscard]] constexpr size_t unroll_index() const noexcept { return UnrollIdx; }
};

struct PartialInvocation {
    size_t rem;
    [[nodiscard]] constexpr PartialInvocation(size_t rem_) noexcept : rem(rem_) {}
    [[nodiscard]] constexpr bool is_partial() const noexcept { return true; }
    [[nodiscard]] constexpr size_t remaining() const noexcept { return rem; }
    [[nodiscard]] constexpr size_t unroll_index() const noexcept { return 0; }
};

// Recursive unroller that will instantiate `UnrollFactor` static calls to `invoke()` on the
// provided invoker type, with each call receiving a constexpr value of BlockInvocation<N> as
// its very first argument. The remaining arguments are forwarded from the caller.
// `N` is in `[0, UnrollFactor)` and is always instantiated in strictly increasing order.
//
// Recursion (rather than folding) is used to limit the inlining depth; we want to use all
// possible dirty tricks to prevent the compiler from ever _not_ inlining what we do here.
//
// Not using the Unroller class since it's primarily meant for lambdas, and these are awkward
// to use with Highway since they must be explicitly HWY_ATTR'd everywhere (without fail) to
// avoid wrong target codegen. OK for the vector kernel itself, a hassle everywhere else.
// Lambdas must also avoid capturing vectors in a closure, as that will cause stack spilling
// and missed optimization opportunities (at least on GCC). I.e. a bit too many footguns.
//
// `UnrollFactor` must be at least 1.
template <size_t UnrollFactor>
struct UnrollingInvoker {
    static_assert(UnrollFactor > 0);

    template <size_t UnrollLbound, size_t UnrollUbound, typename Invoker, typename... Args>
    HWY_API void do_invoke_unrolled(Args&&... args) noexcept {
        if constexpr (UnrollLbound == UnrollUbound) { // Leaf recursion case
            constexpr size_t unroll_idx = UnrollLbound;
            Invoker::invoke(BlockInvocation<unroll_idx>{}, std::forward<Args>(args)...);
        } else {
            static_assert(UnrollLbound < UnrollUbound);
            constexpr size_t half = (UnrollUbound - UnrollLbound) / 2;
            do_invoke_unrolled<UnrollLbound, UnrollLbound + half, Invoker>(std::forward<Args>(args)...);
            if constexpr (half > 0) {
                do_invoke_unrolled<UnrollLbound + half, UnrollUbound, Invoker>(std::forward<Args>(args)...);
            }
        }
    }

    // Important: no argument in `args` shall be a move-only type, or behavior is undefined.
    template <typename Invoker, typename... Args>
    HWY_API void invoke(Args&&... args) noexcept {
        do_invoke_unrolled<0, UnrollFactor, Invoker>(std::forward<Args>(args)...);
    }
};

template <typename T, bool BigEndian>
struct UnpackBitsInvoker;

template <bool BigEndian>
struct UnpackBitsInvoker<int8_t, BigEndian> {
    template <InvocationMeta MetaT, typename DI8>
    HWY_API void invoke(MetaT meta, DI8 di8, const uint8_t* HWY_RESTRICT src_u8, int8_t* HWY_RESTRICT dest,
                        const size_t NB, const size_t my_i, size_t& my_out_idx) noexcept {
        HWY_LANES_CONSTEXPR size_t NI8 = hn::Lanes(di8);
        using VecI8 = hn::VFromD<decltype(di8)>;

        VecI8 bits_i8 =
            hn::IfThenElseZero(hn::LoadMaskBits(di8, src_u8 + my_i + (NB * meta.unroll_index())), hn::Set(di8, 1));
        if constexpr (BigEndian) {
            bits_i8 = hn::Reverse8(di8, bits_i8);
        }
        if constexpr (!meta.is_partial()) {
            hn::StoreU(bits_i8, di8, dest + my_out_idx);
            my_out_idx += NI8;
        } else {
            //  meta.remaining() is in number of input _bytes_
            const size_t rem_out_bytes = meta.remaining() * 8;
            hn::StoreN(bits_i8, di8, dest + my_out_idx, rem_out_bytes);
            my_out_idx += rem_out_bytes;
        }
    };
};

template <bool BigEndian>
struct UnpackBitsInvoker<hwy::bfloat16_t, BigEndian> {
    template <InvocationMeta MetaT, typename DI8>
    [[maybe_unused]]
    HWY_API void invoke(MetaT meta, DI8 di8, const uint8_t* HWY_RESTRICT src_u8, hwy::bfloat16_t* HWY_RESTRICT dest,
                        const size_t NB, const size_t my_i, size_t& my_out_idx) noexcept {
        const hn::RepartitionToWide<decltype(di8)>        di16;
        const hn::Rebind<hwy::bfloat16_t, decltype(di16)> dbf16;
        const hn::Half<decltype(di8)>                     di8_half;

        using VecBF16 = hn::VFromD<decltype(dbf16)>;

        HWY_LANES_CONSTEXPR size_t NBF16 = hn::Lanes(dbf16);

        auto bm = hn::LoadMaskBits(di8, src_u8 + my_i + (NB * meta.unroll_index()));
        if constexpr (BigEndian) {
            bm = hn::MaskFromVec(hn::Reverse8(di8, hn::VecFromMask(di8, bm)));
        }
        auto bm_lo = hn::PromoteMaskTo(dbf16, di8_half, hn::LowerHalfOfMask(di8_half, bm));
        auto bm_hi = hn::PromoteMaskTo(dbf16, di8_half, hn::UpperHalfOfMask(di8_half, bm));

        VecBF16 bf16_lo = hn::IfThenElseZero(bm_lo, hn::Set(dbf16, hwy::bfloat16_t(1)));
        VecBF16 bf16_hi = hn::IfThenElseZero(bm_hi, hn::Set(dbf16, hwy::bfloat16_t(1)));

        if constexpr (!meta.is_partial()) {
            hn::StoreU(bf16_lo, dbf16, dest + my_out_idx);
            my_out_idx += NBF16;
            hn::StoreU(bf16_hi, dbf16, dest + my_out_idx);
            my_out_idx += NBF16;
        } else {
            size_t rem_out_bf16s = meta.remaining() * 8;

            size_t to_write = std::min(rem_out_bf16s, NBF16);
            hn::StoreN(bf16_lo, dbf16, dest + my_out_idx, to_write);
            rem_out_bf16s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_bf16s, NBF16);
            hn::StoreN(bf16_hi, dbf16, dest + my_out_idx, to_write);
            rem_out_bf16s -= to_write;
            my_out_idx += to_write;
        }
    };
};

template <bool BigEndian>
struct UnpackBitsInvoker<float, BigEndian> {

    template <InvocationMeta MetaT, typename DI8>
    [[maybe_unused]]
    HWY_API void invoke(MetaT meta, DI8 di8, const uint8_t* HWY_RESTRICT src_u8, float* HWY_RESTRICT dest,
                        const size_t NB, const size_t my_i, size_t& my_out_idx) noexcept {
        const hn::RepartitionToWide<decltype(di8)>  di16;
        const hn::RepartitionToWide<decltype(di16)> di32;
        const hn::RebindToFloat<decltype(di32)>     df32;

        using VecF32 = hn::VFromD<decltype(df32)>;

        HWY_LANES_CONSTEXPR size_t NF32 = hn::Lanes(df32);

        auto bm = hn::LoadMaskBits(di8, src_u8 + my_i + (NB * meta.unroll_index()));
        if constexpr (BigEndian) {
            bm = hn::MaskFromVec(hn::Reverse8(di8, hn::VecFromMask(di8, bm)));
        }

        auto bm_0 = MyPromoteLowerMaskTo(di16, di8, bm);
        auto bm_1 = MyPromoteUpperMaskTo(di16, di8, bm);

        auto bm_0_0 = MyPromoteLowerMaskTo(df32, di16, bm_0);
        auto bm_0_1 = MyPromoteUpperMaskTo(df32, di16, bm_0);
        auto bm_1_0 = MyPromoteLowerMaskTo(df32, di16, bm_1);
        auto bm_1_1 = MyPromoteUpperMaskTo(df32, di16, bm_1);

        VecF32 f32_0_0 = hn::IfThenElseZero(bm_0_0, hn::Set(df32, 1.0f));
        VecF32 f32_0_1 = hn::IfThenElseZero(bm_0_1, hn::Set(df32, 1.0f));
        VecF32 f32_1_0 = hn::IfThenElseZero(bm_1_0, hn::Set(df32, 1.0f));
        VecF32 f32_1_1 = hn::IfThenElseZero(bm_1_1, hn::Set(df32, 1.0f));

        if constexpr (!meta.is_partial()) {
            hn::StoreU(f32_0_0, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_0_1, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_1_0, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_1_1, df32, dest + my_out_idx);
            my_out_idx += NF32;
        } else {
            size_t rem_out_f32s = meta.remaining() * 8;

            size_t to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_0_0, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_0_1, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_1_0, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = rem_out_f32s;
            hn::StoreN(f32_1_1, df32, dest + my_out_idx, to_write);
            my_out_idx += to_write;
        }
    };

    template <InvocationMeta MetaT, typename DI8>
    [[maybe_unused]]
    HWY_API void invoke3(MetaT meta, DI8 di8, const uint8_t* HWY_RESTRICT src_u8, float* HWY_RESTRICT dest,
                        const size_t NB, const size_t my_i, size_t& my_out_idx) noexcept {
        const hn::RepartitionToWide<decltype(di8)>  di16;
        const hn::RepartitionToWide<decltype(di16)> di32;
        const hn::RebindToFloat<decltype(di32)>     df32;
        const hn::Half<decltype(di8)>               di8_half;
        const hn::Half<decltype(di16)>              di16_half;

        using VecF32 = hn::VFromD<decltype(df32)>;

        HWY_LANES_CONSTEXPR size_t NF32 = hn::Lanes(df32);

        auto bm = hn::LoadMaskBits(di8, src_u8 + my_i + (NB * meta.unroll_index()));
        if constexpr (BigEndian) {
            bm = hn::MaskFromVec(hn::Reverse8(di8, hn::VecFromMask(di8, bm)));
        }

        auto bm_0 = hn::PromoteMaskTo(di16, di8_half, hn::LowerHalfOfMask(di8_half, bm));
        auto bm_1 = hn::PromoteMaskTo(di16, di8_half, hn::UpperHalfOfMask(di8_half, bm));

        auto bm_0_0 = hn::PromoteMaskTo(df32, di16_half, hn::LowerHalfOfMask(di16_half, bm_0));
        auto bm_0_1 = hn::PromoteMaskTo(df32, di16_half, hn::UpperHalfOfMask(di16_half, bm_0));
        auto bm_1_0 = hn::PromoteMaskTo(df32, di16_half, hn::LowerHalfOfMask(di16_half, bm_1));
        auto bm_1_1 = hn::PromoteMaskTo(df32, di16_half, hn::UpperHalfOfMask(di16_half, bm_1));

        VecF32 f32_0_0 = hn::IfThenElseZero(bm_0_0, hn::Set(df32, 1.0f));
        VecF32 f32_0_1 = hn::IfThenElseZero(bm_0_1, hn::Set(df32, 1.0f));
        VecF32 f32_1_0 = hn::IfThenElseZero(bm_1_0, hn::Set(df32, 1.0f));
        VecF32 f32_1_1 = hn::IfThenElseZero(bm_1_1, hn::Set(df32, 1.0f));

        if constexpr (!meta.is_partial()) {
            hn::StoreU(f32_0_0, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_0_1, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_1_0, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_1_1, df32, dest + my_out_idx);
            my_out_idx += NF32;
        } else {
            size_t rem_out_f32s = meta.remaining() * 8;

            size_t to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_0_0, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_0_1, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_1_0, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = rem_out_f32s;
            hn::StoreN(f32_1_1, df32, dest + my_out_idx, to_write);
            my_out_idx += to_write;
        }
    };

    template <InvocationMeta MetaT, typename DI8>
    [[maybe_unused]]
    HWY_API void invoke2(MetaT meta, DI8 di8, const uint8_t* HWY_RESTRICT src_u8, float* HWY_RESTRICT dest,
                         const size_t NB, const size_t my_i, size_t& my_out_idx) noexcept {
        const hn::RepartitionToWide<decltype(di8)>  di16;
        const hn::RepartitionToWide<decltype(di16)> di32;
        const hn::RebindToFloat<decltype(di32)>     df32;

        using VecI8 = hn::VFromD<decltype(di8)>;
        using VecI16 = hn::VFromD<decltype(di16)>;
        using VecF32 = hn::VFromD<decltype(df32)>;

        HWY_LANES_CONSTEXPR size_t NF32 = hn::Lanes(df32);

        // Magic number: 127 is the biased float32 exponent for 1.0 (with a mantissa of zero)
        VecI8 bits_i8 =
            hn::IfThenElseZero(hn::LoadMaskBits(di8, src_u8 + my_i + (NB * meta.unroll_index())), hn::Set(di8, 127));
        if constexpr (BigEndian) {
            bits_i8 = hn::Reverse8(di8, bits_i8);
        }
        VecI16 bits_i16_lo = hn::PromoteLowerTo(di16, bits_i8);
        VecI16 bits_i16_hi = hn::PromoteUpperTo(di16, bits_i8);
        // Shift biased exponents into float32 MSBs (leaving sign bit and mantissa zeroed)
        VecF32 f32_lo_lo = hn::BitCast(df32, hn::ShiftLeft<23>(hn::PromoteLowerTo(di32, bits_i16_lo)));
        VecF32 f32_lo_hi = hn::BitCast(df32, hn::ShiftLeft<23>(hn::PromoteUpperTo(di32, bits_i16_lo)));
        VecF32 f32_hi_lo = hn::BitCast(df32, hn::ShiftLeft<23>(hn::PromoteLowerTo(di32, bits_i16_hi)));
        VecF32 f32_hi_hi = hn::BitCast(df32, hn::ShiftLeft<23>(hn::PromoteUpperTo(di32, bits_i16_hi)));
        if constexpr (!meta.is_partial()) {
            hn::StoreU(f32_lo_lo, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_lo_hi, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_hi_lo, df32, dest + my_out_idx);
            my_out_idx += NF32;
            hn::StoreU(f32_hi_hi, df32, dest + my_out_idx);
            my_out_idx += NF32;
        } else {
            size_t rem_out_f32s = meta.remaining() * 8;

            size_t to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_lo_lo, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_lo_hi, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f32s, NF32);
            hn::StoreN(f32_hi_lo, df32, dest + my_out_idx, to_write);
            rem_out_f32s -= to_write;
            my_out_idx += to_write;

            to_write = rem_out_f32s;
            hn::StoreN(f32_hi_hi, df32, dest + my_out_idx, to_write);
            my_out_idx += to_write;
        }
    };
};

template <bool BigEndian>
struct UnpackBitsInvoker<double, BigEndian> {
    template <InvocationMeta MetaT, typename DI8>
    HWY_API void invoke(MetaT meta, DI8 di8, const uint8_t* HWY_RESTRICT src_u8, double* HWY_RESTRICT dest,
                        const size_t NB, const size_t my_i, size_t& my_out_idx) noexcept {
        const hn::RepartitionToWide<decltype(di8)>  di16;
        const hn::RepartitionToWide<decltype(di16)> di32;
        const hn::RepartitionToWide<decltype(di32)> di64;
        const hn::RebindToFloat<decltype(di64)>     df64;

        using VecF64 = hn::VFromD<decltype(df64)>;

        HWY_LANES_CONSTEXPR size_t NF64 = hn::Lanes(df64);

        auto bm = hn::LoadMaskBits(di8, src_u8 + my_i + (NB * meta.unroll_index()));
        if constexpr (BigEndian) {
            bm = hn::MaskFromVec(hn::Reverse8(di8, hn::VecFromMask(di8, bm)));
        }

        auto bm_0 = MyPromoteLowerMaskTo(di16, di8, bm);
        auto bm_1 = MyPromoteUpperMaskTo(di16, di8, bm);

        auto bm_0_0 = MyPromoteLowerMaskTo(di32, di16, bm_0);
        auto bm_0_1 = MyPromoteUpperMaskTo(di32, di16, bm_0);
        auto bm_1_0 = MyPromoteLowerMaskTo(di32, di16, bm_1);
        auto bm_1_1 = MyPromoteUpperMaskTo(di32, di16, bm_1);

        // We shall count our blessings that there's no float128 to support.
        auto bm_0_0_0 = MyPromoteLowerMaskTo(df64, di32, bm_0_0);
        auto bm_0_0_1 = MyPromoteUpperMaskTo(df64, di32, bm_0_0);
        auto bm_0_1_0 = MyPromoteLowerMaskTo(df64, di32, bm_0_1);
        auto bm_0_1_1 = MyPromoteUpperMaskTo(df64, di32, bm_0_1);
        auto bm_1_0_0 = MyPromoteLowerMaskTo(df64, di32, bm_1_0);
        auto bm_1_0_1 = MyPromoteUpperMaskTo(df64, di32, bm_1_0);
        auto bm_1_1_0 = MyPromoteLowerMaskTo(df64, di32, bm_1_1);
        auto bm_1_1_1 = MyPromoteUpperMaskTo(df64, di32, bm_1_1);

        VecF64 f64_0_0_0 = hn::IfThenElseZero(bm_0_0_0, hn::Set(df64, 1.0));
        VecF64 f64_0_0_1 = hn::IfThenElseZero(bm_0_0_1, hn::Set(df64, 1.0));
        VecF64 f64_0_1_0 = hn::IfThenElseZero(bm_0_1_0, hn::Set(df64, 1.0));
        VecF64 f64_0_1_1 = hn::IfThenElseZero(bm_0_1_1, hn::Set(df64, 1.0));
        VecF64 f64_1_0_0 = hn::IfThenElseZero(bm_1_0_0, hn::Set(df64, 1.0));
        VecF64 f64_1_0_1 = hn::IfThenElseZero(bm_1_0_1, hn::Set(df64, 1.0));
        VecF64 f64_1_1_0 = hn::IfThenElseZero(bm_1_1_0, hn::Set(df64, 1.0));
        VecF64 f64_1_1_1 = hn::IfThenElseZero(bm_1_1_1, hn::Set(df64, 1.0));
        // Before anyone asks, no you can't have arrays of SIMD vector registers.
        // One good reason is that their size is not known at compile-time for SVE/SVE2.
        if constexpr (!meta.is_partial()) {
            hn::StoreU(f64_0_0_0, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_0_0_1, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_0_1_0, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_0_1_1, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_1_0_0, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_1_0_1, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_1_1_0, df64, dest + my_out_idx);
            my_out_idx += NF64;
            hn::StoreU(f64_1_1_1, df64, dest + my_out_idx);
            my_out_idx += NF64;
        } else {
            size_t rem_out_f64s = meta.remaining() * 8;

            size_t to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_0_0_0, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_0_0_1, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_0_1_0, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_0_1_1, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_1_0_0, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_1_0_1, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_1_1_0, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;

            to_write = std::min(rem_out_f64s, NF64);
            hn::StoreN(f64_1_1_1, df64, dest + my_out_idx, to_write);
            rem_out_f64s -= to_write;
            my_out_idx += to_write;
        }
    };
};

template <typename T, bool BigEndian, size_t UnrollBy>
[[maybe_unused]]
HWY_NOINLINE void my_hwy_unpack_bits(const int8_t* HWY_RESTRICT src, T* HWY_RESTRICT dest, size_t sz) noexcept {
    const hn::ScalableTag<int8_t> di8;

    const auto* src_u8 = reinterpret_cast<const uint8_t*>(src);

    HWY_LANES_CONSTEXPR size_t N = hn::Lanes(di8);
    HWY_LANES_CONSTEXPR size_t NB = N / 8;

    size_t i = 0, out_idx = 0;
    for (; i + NB * UnrollBy <= sz; i += NB * UnrollBy) {
        UnrollingInvoker<UnrollBy>::template invoke<UnpackBitsInvoker<T, BigEndian>>(di8, src_u8, dest, NB, i,
                                                                                     out_idx);
    }
    for (; i + NB <= sz; i += NB) {
        UnpackBitsInvoker<T, BigEndian>::invoke(BlockInvocation<0>{}, di8, src_u8, dest, NB, i, out_idx);
    }
    const size_t rem = sz - i;
    if (rem > 0) {
        CHECK(rem <= NB);
        // LoadMaskBits must have at least 8 bytes readable. The value of any unused padding bytes
        // are treated as-if zero, and can be left uninitialized by us.
        uint8_t tmp[std::max(hn::MaxLanes(di8) / 8, size_t{8})];
        memcpy(tmp, src_u8 + i, rem);
        UnpackBitsInvoker<T, BigEndian>::invoke(PartialInvocation{rem}, di8, tmp, dest, NB, 0, out_idx);
    }
}

HWY_INLINE
const char* my_hwy_target_name() noexcept {
    return hwy::TargetName(HWY_TARGET);
}

[[nodiscard]] uint16_t vector_byte_size() noexcept {
    const hn::ScalableTag<int8_t> d8;            // Widest possible runtime vector
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
double my_squared_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return my_hwy_square_euclidean_distance_int8(a, b, sz);
}
double my_squared_euclidean_distance_bf16(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    return my_hwy_squared_euclidean_distance_bf16(a, b, sz);
}
double my_squared_euclidean_distance_f32(const float* a, const float* b, size_t sz) noexcept {
    return my_hwy_squared_euclidean_distance(a, b, sz);
}
double my_squared_euclidean_distance_f64(const double* a, const double* b, size_t sz) noexcept {
    return my_hwy_squared_euclidean_distance(a, b, sz);
}
float my_squared_euclidean_length_f32(const float* a, size_t sz) noexcept {
    return my_hwy_squared_euclidean_length(a, sz);
}
size_t my_binary_hamming_distance(const void* lhs, const void* rhs, size_t sz) noexcept {
    return my_hwy_binary_hamming_distance(lhs, rhs, sz);
}
size_t my_population_count(const uint64_t* buf, size_t sz) noexcept {
    return my_hwy_popcount(buf, sz);
}
void my_unpack_bits_i8(const int8_t* src_bits, int8_t* dest, size_t sz, bool big_endian) noexcept {
    if (!big_endian) {
        my_hwy_unpack_bits<int8_t, false, 4>(src_bits, dest, sz);
    } else {
        my_hwy_unpack_bits<int8_t, true, 4>(src_bits, dest, sz);
    }
}
void my_unpack_bits_bf16(const int8_t* src_bits, BFloat16* dest, size_t sz, bool big_endian) noexcept {
    auto* hwy_bf16_dest = reinterpret_cast<hwy::bfloat16_t*>(dest);
    if (!big_endian) {
        my_hwy_unpack_bits<hwy::bfloat16_t, false, 1>(src_bits, hwy_bf16_dest, sz);
    } else {
        my_hwy_unpack_bits<hwy::bfloat16_t, true, 1>(src_bits, hwy_bf16_dest, sz);
    }
}
void my_unpack_bits_f32(const int8_t* src_bits, float* dest, size_t sz, bool big_endian) noexcept {
    if (!big_endian) {
        my_hwy_unpack_bits<float, false, 1>(src_bits, dest, sz);
    } else {
        my_hwy_unpack_bits<float, true, 1>(src_bits, dest, sz);
    }
}
void my_unpack_bits_f64(const int8_t* src_bits, double* dest, size_t sz, bool big_endian) noexcept {
    if (!big_endian) {
        my_hwy_unpack_bits<double, false, 1>(src_bits, dest, sz);
    } else {
        my_hwy_unpack_bits<double, true, 1>(src_bits, dest, sz);
    }
}
TargetInfo my_target_info() noexcept {
    return {"Highway", my_hwy_target_name(), vector_byte_size()};
}

} // namespace

class HwyTargetAccelerator final : public PlatformGenericAccelerator {
public:
    ~HwyTargetAccelerator() override = default;

    TargetInfo target_info() const noexcept override { return my_target_info(); }

    [[nodiscard]] static dispatch::FnTable build_fn_table() {
        using dispatch::FnTable;
        FnTable ft(my_target_info());
        ft.dot_product_i8 = my_dot_product_i8;
        ft.dot_product_bf16 = my_dot_product_bf16;
        ft.dot_product_f32 = my_dot_product_f32;
        ft.dot_product_f64 = my_dot_product_f64;
        ft.squared_euclidean_distance_i8 = my_squared_euclidean_distance_i8;
        ft.squared_euclidean_distance_bf16 = my_squared_euclidean_distance_bf16;
        ft.squared_euclidean_distance_f32 = my_squared_euclidean_distance_f32;
        ft.squared_euclidean_distance_f64 = my_squared_euclidean_distance_f64;
        ft.squared_euclidean_length_f32 = my_squared_euclidean_length_f32;
        ft.binary_hamming_distance = my_binary_hamming_distance;
        ft.population_count = my_population_count;
        ft.unpack_bits_to_8x_i8 = my_unpack_bits_i8;
        ft.unpack_bits_to_8x_bf16 = my_unpack_bits_bf16;
        ft.unpack_bits_to_8x_f32 = my_unpack_bits_f32;
        ft.unpack_bits_to_8x_f64 = my_unpack_bits_f64;
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

} // namespace HWY_NAMESPACE
} // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#if HWY_ONCE

namespace vespalib::hwaccelerated {

namespace {

#define VESPA_HWY_ADD_SUPPORTED_IMPL_VISITOR(hwy_target, hwy_ns)                                      \
    if ((supported_targets & hwy_target) != 0) {                                                      \
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
    std::ranges::sort(target_id_and_impl,
                      [](const auto& lhs, const auto& rhs) noexcept { return lhs.first < rhs.first; });
    // We might get a duplicate output due to HWY_VISIT_TARGETS including the fallback static
    // target, which is likely equal to another target. Remove dupes to avoid having to deal
    // with this at a higher level.
    auto to_erase = std::ranges::unique(
        target_id_and_impl, [](const auto& lhs, const auto& rhs) noexcept { return lhs.first == rhs.first; });
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

} // namespace

std::vector<std::unique_ptr<IAccelerated>> Highway::create_supported_targets() {
    return create_supported_targets_impl();
}

} // namespace vespalib::hwaccelerated

#endif // HWY_ONCE
