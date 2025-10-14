// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "highway.h"
#include "platform_generic.h"
#include <hwy/base.h>
#include <algorithm>
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

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;

// Must always be undef'd to ensure we expand the correct HWY_ATTR per target.
#undef VESPA_NOEXCEPT_HWY_ATTR
// Clang and GCC refuse to parse `noexcept HWY_ATTR` and `HWY_ATTR noexcept`,
// respectively. GCC seems to be the one that is technically correct(tm), but
// we still want Clang to compile, so hide the dirt under a macro carpet.
#if defined(__clang__)
#define VESPA_NOEXCEPT_HWY_ATTR HWY_ATTR noexcept
#else
#define VESPA_NOEXCEPT_HWY_ATTR noexcept HWY_ATTR
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
    auto kernel_fn = [df32](auto lhs, auto rhs, auto& acc0, auto& acc1) VESPA_NOEXCEPT_HWY_ATTR {
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
    // `HWY_ATTR` is needed to ensure lambdas have the expected codegen target.
    // See https://google.github.io/highway/en/master/faq.html#boilerplate
    const auto kernel_fn = [](auto lhs, auto rhs, auto& accu) VESPA_NOEXCEPT_HWY_ATTR {
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

    auto kernel_fn = [df](auto lhs, auto rhs, auto& acc0, auto& acc1) VESPA_NOEXCEPT_HWY_ATTR {
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

    auto kernel_fn = [d16, d32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_NOEXCEPT_HWY_ATTR {
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
    // by the number of worst-case sums of -128**2 that can fit into an i32.
    constexpr size_t max_n_per_chunk = INT32_MAX / (INT8_MIN*INT8_MIN);
    return compute_chunked_sum<max_n_per_chunk, double>(sub_mul_add_i8_to_i32, a, b, sz);
}

HWY_INLINE
size_t my_hwy_popcount(const uint64_t* a, const size_t sz) noexcept {
    // TODO have a way to explicitly disable fallbacks for benchmarking purposes
#if HWY_TARGET != HWY_AVX2 && HWY_TARGET != HWY_AVX3
    const hn::ScalableTag<uint64_t> d;
    const auto kernel_fn = [](auto v, auto& accu) VESPA_NOEXCEPT_HWY_ATTR {
        accu = hn::Add(hn::PopulationCount(v), accu);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::elementwise(d, d, a, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
#else
    // AVX2 and AVX3 do not have dedicated vector popcount instructions, so the Highway "emulation"
    // ends up being slower in practice than the baseline one using 4x pipelined POPCNT.
    return PlatformGenericAccelerator().populationCount(a, sz);
#endif
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
    const auto kernel_fn = [d32](auto lhs_i8, auto rhs_i8, auto& accu) VESPA_NOEXCEPT_HWY_ATTR {
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

    const auto kernel_fn = [d16, d32](auto lhs_i8, auto rhs_i8, auto& acc0, auto& acc1, auto& acc2, auto& acc3) VESPA_NOEXCEPT_HWY_ATTR {
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

HWY_INLINE
const char* my_hwy_target_name() noexcept {
    return hwy::TargetName(HWY_TARGET);
}

[[nodiscard]] size_t vector_bit_count() noexcept {
    const hn::ScalableTag<int8_t> d8; // Widest possible runtime vector
    return hn::Lanes(d8) * 8;
}

// Since we already do a virtual dispatch via the IAccelerated interface, avoid needing an
// additional per-function dispatch step via Highway's function tables by creating a concrete
// implementation class per target.
class HwyTargetAccelerator final : public PlatformGenericAccelerator {
public:
    ~HwyTargetAccelerator() override = default;
    float dotProduct(const float* a, const float* b, size_t sz) const noexcept override {
        return my_hwy_dot_float(a, b, sz);
    }
    float dotProduct(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept override {
        return my_hwy_dot_bf16(a, b, sz);
    }
    double dotProduct(const double* a, const double* b, size_t sz) const noexcept override {
        return my_hwy_dot_double(a, b, sz);
    }
    int64_t dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept override {
        return my_hwy_dot_int8(a, b, sz);
    }
    size_t populationCount(const uint64_t* a, size_t sz) const noexcept override {
        return my_hwy_popcount(a, sz);
    }
    double squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept override {
        return my_hwy_square_euclidean_distance_int8(a, b, sz);
    }
    double squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept override {
        return my_hwy_square_euclidean_distance(a, b, sz);
    }
    double squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept override {
        return my_hwy_square_euclidean_distance(a, b, sz);
    }
    double squaredEuclideanDistance(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept override {
        return my_hwy_square_euclidean_distance_bf16(a, b, sz);
    }
    const char* implementation_name() const noexcept override {
        return "Highway";
    }
    const char* target_name() const noexcept override {
        return my_hwy_target_name();
    }
    std::string friendly_name() const override {
        return std::format("Highway - {} ({} bit vector width)", target_name(), vector_bit_count());
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

enum class ExcludeTargets {
    // No targets should be excluded
    None,
    // Exclude targets that _we_ believe are not optimal for the purposes of running our
    // vectorized kernels.
    AssumedSuboptimal
};

bool target_is_assumed_suboptimal(uint64_t target_hwy_id) noexcept {
    // SVE/SVE2 is not a strict superset of NEON, which means that certain very useful
    // 128-bit NEON(_BF16) vector instructions are _not_ present as "sizeless" SVE vector
    // operations.
    //
    // In particular:
    //  - int8 squared Euclidean distance:
    //    NEON has `ssub` signed subtraction of high/low vector lanes with implicit
    //    widening. On SVE this needs separate unpack high/low instructions followed by
    //    a non-widening subtraction, increasing instruction count and register pressure.
    //  - BFloat16 dot product:
    //    SVE does not have guaranteed BF16 support prior to armv8.6-a and its BF16 dot
    //    product operation does not give the same result as NEON BF16 unless FEAT_EBF16
    //    is present, due to differences in rounding behavior. Because of this, dynamic
    //    target compilation for Highway does not by default use BF16 instructions for
    //    _any_ SVE targets. It is also not clear how this could be enabled with today's
    //    set of compilation targets, as they are not ARM architecture version-oriented.
    //
    // In practice this means that 128-bit SVE may be _slower_ for some important operations
    // than 128-bit NEON_BF16. For both the above ops, the observed relative slowdown is
    // on the order of ~1.5-2x. The only serious speed increase from SVE is for popcount.
    //
    // So for now, disable SVE targets entirely until it's had more time to cook. If SVE
    // is present, NEON_BF16 is expected to always be present.
    //
    // This is based on testing on Google Axion (SVE2_128), Amazon Graviton 3 (SVE_256)
    // and Amazon Graviton 4 (SVE2_128) nodes, and will be updated once newer/shinier
    // hardware is available for testing.
    //
    // TODO consider still enabling if SVE2 vector length is > 128 bits. Needs benchmarking.
    //  Only HW with >128 bits that's currently available is Graviton 3, which is only SVE.
    return (target_hwy_id & HWY_ALL_SVE) != 0;
}

// The "best" supported target will be the first element in the vector.
std::vector<std::unique_ptr<IAccelerated>> create_supported_targets_impl(ExcludeTargets exclude_targets) {
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

    std::vector<std::unique_ptr<IAccelerated>> preferred_target_order;
    preferred_target_order.reserve(target_id_and_impl.size());
    for (auto& elem : target_id_and_impl) {
        if ((exclude_targets == ExcludeTargets::AssumedSuboptimal) && target_is_assumed_suboptimal(elem.first)) {
            continue; // Ignore this target
        }
        preferred_target_order.emplace_back(std::move(elem.second));
    }
    assert(!preferred_target_order.empty()); // Must be at least a fallback target
    return preferred_target_order;
}

} // anon ns

std::vector<std::unique_ptr<IAccelerated>> Highway::create_supported_targets() {
    return create_supported_targets_impl(ExcludeTargets::None);
}

std::unique_ptr<IAccelerated> Highway::create_best_target() {
    return std::move(create_supported_targets_impl(ExcludeTargets::AssumedSuboptimal).front());
}

} // namespace vespalib::hwaccelerated

#endif // HWY_ONCE
