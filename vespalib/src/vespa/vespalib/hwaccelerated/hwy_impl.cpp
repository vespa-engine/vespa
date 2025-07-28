// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hwy_impl.h"
#include <hwy/base.h>
#include <cassert>

// 0 => static dispatch (single target)
// 1 => dynamic dispatch (multiple targets)
#define VESPA_HWY_DYNAMIC 1

#if VESPA_HWY_DYNAMIC
#  undef HWY_TARGET_INCLUDE
#  define HWY_TARGET_INCLUDE "vespa/vespalib/hwaccelerated/hwy_impl.cpp"
#  include <hwy/foreach_target.h>
#endif // VESPA_HWY_DYNAMIC

#include "hwy_kernel-inl.h"
#include <hwy/contrib/dot/dot-inl.h>

#if VESPA_HWY_DYNAMIC
// noexcept not supported for dynamic dispatch target functions
#define VESPA_HWY_NOEXCEPT
HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {
#else
#define VESPA_HWY_NOEXCEPT noexcept
namespace vespalib::hwaccelerated {
#endif // VESPA_HWY_DYNAMIC

namespace hn = hwy::HWY_NAMESPACE;

template <typename T> //requires (hwy::IsFloat<T>() || hwy::IsSame<T, hwy::bfloat16_t>>())
HWY_INLINE T
my_hwy_dot_impl(const T* HWY_RESTRICT a, const T* HWY_RESTRICT b, size_t sz) noexcept {
    const hn::ScalableTag<T> d;
    return hwy::ConvertScalarTo<T>(hn::Dot::Compute<0>(d, a, b, sz));
}

HWY_NOINLINE float
my_hwy_dot_float(const float* HWY_RESTRICT a, const float* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_dot_impl(a, b, sz);
}

HWY_NOINLINE float
my_hwy_dot_bf16(const BFloat16* HWY_RESTRICT a, const BFloat16* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    // Highway already comes with dot product kernels supporting BF16, so use these.
    static_assert(sizeof(BFloat16)  == sizeof(hwy::bfloat16_t));
    static_assert(alignof(BFloat16) == alignof(hwy::bfloat16_t));

    const auto* a_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(a);
    const auto* b_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(b);
    return my_hwy_dot_impl(a_bf16, b_bf16, sz);
}

HWY_NOINLINE double
my_hwy_dot_double(const double* HWY_RESTRICT a, const double* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_dot_impl(a, b, sz);
}

template <typename T> requires (hwy::IsFloat<T>())
HWY_NOINLINE double
my_hwy_square_euclidean_distance_unrolled_impl(const T* a, const T* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<T> d;
    const auto kernel_fn = [](auto lhs, auto rhs, auto& accu) noexcept {
        const auto sub = hn::Sub(lhs, rhs);
        accu = hn::MulAdd(sub, sub, accu); // note: using fused multiply-add
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d, d, a, b, sz, hn::Zero(d), kernel_fn, VecAdd(), LaneReduceSum());
}

HWY_NOINLINE double
my_hwy_bf16_square_euclidean_distance_unrolled(const BFloat16* a, const BFloat16* b, size_t sz) VESPA_HWY_NOEXCEPT {
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
    const hn::Repartition<float, decltype(dbf16)> df;
    // Since we're widening the element type, loading e.g. 8 lanes of BF16 in a single
    // 128-bit vector requires us to process 2 vectors of 4 lanes of float32.
    auto accu = [df](auto lhs, auto rhs, auto& acc0, auto& acc1) noexcept {
        const auto sub_lo = hn::Sub(hn::PromoteLowerTo(df, lhs), hn::PromoteLowerTo(df, rhs));
        acc0 = hn::MulAdd(sub_lo, sub_lo, acc0);
        const auto sub_hi = hn::Sub(hn::PromoteUpperTo(df, lhs), hn::PromoteUpperTo(df, rhs));
        acc1 = hn::MulAdd(sub_hi, sub_hi, acc1);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<4>, UnrolledBy<2>, HasAccumulatorArity<2>>;
    return MyKernel::pairwise(dbf16, df, a_bf16, b_bf16, sz, hn::Zero(df), accu, VecAdd(), LaneReduceSum());
}

// Important: `sz` should be low enough that the intermediate i32 sum does not overflow!
HWY_NOINLINE int32_t
sub_mul_add_i8s_via_i16_to_i32(const int8_t* a, const int8_t* b, size_t sz) {
    const hn::ScalableTag<int8_t>                  d8;
    const hn::Repartition<int16_t, decltype(d8)>  d16;
    const hn::Repartition<int32_t, decltype(d16)> d32;

    auto accu = [d16, d32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) noexcept {
        const auto sub_l_i16 = hn::Sub(hn::PromoteLowerTo(d16, lhs), hn::PromoteLowerTo(d16, rhs));
        const auto sub_u_i16 = hn::Sub(hn::PromoteUpperTo(d16, lhs), hn::PromoteUpperTo(d16, rhs));
        acc0 = hn::ReorderWidenMulAccumulate(d32, sub_l_i16, sub_l_i16, acc0, acc1);
        acc2 = hn::ReorderWidenMulAccumulate(d32, sub_u_i16, sub_u_i16, acc2, acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<4>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), accu, VecAdd(), LaneReduceSum());
}

HWY_NOINLINE double
my_hwy_square_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) VESPA_HWY_NOEXCEPT {
    // If we cannot possibly overflow intermediate i32 accumulators we can directly
    // compute the distance without requiring any chunking. Max chunk size is defined
    // by the number of worst-case sums of -128**2 that can fit into an i32.
    constexpr size_t max_n_per_chunk = INT32_MAX / (INT8_MIN*INT8_MIN);
    if (sz <= max_n_per_chunk) [[likely]] {
        return sub_mul_add_i8s_via_i16_to_i32(a, b, sz);
    }
    // Process input in chunks that are small enough that the intermediate i32 accumulators
    // won't overflow, but large enough that we can spin up the vector steam engines fully.
    // TODO explicitly test this fallback path
    double sum = 0;
    size_t i = 0;
    for (; i + max_n_per_chunk <= sz; i += max_n_per_chunk) {
        sum += sub_mul_add_i8s_via_i16_to_i32(a + i, b + i, max_n_per_chunk);
    }
    if (sz > i) {
        sum += sub_mul_add_i8s_via_i16_to_i32(a + i, b + i, sz - i);
    }
    return sum;
}

HWY_NOINLINE double
my_hwy_square_euclidean_distance_float_unrolled(const float* a, const float* b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_square_euclidean_distance_unrolled_impl(a, b, sz);
}

HWY_NOINLINE double
my_hwy_square_euclidean_distance_double_unrolled(const double* a, const double* b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_square_euclidean_distance_unrolled_impl(a, b, sz);
}

HWY_NOINLINE size_t
my_hwy_popcount(const uint64_t* a, size_t sz) VESPA_HWY_NOEXCEPT {
    // TODO have a way to explicitly disable fallbacks for benchmarking purposes
#if HWY_TARGET != HWY_AVX2 && HWY_TARGET != HWY_AVX3
    const hn::ScalableTag<uint64_t> d;
    const auto kernel_fn = [](auto v, auto& accu) noexcept {
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

HWY_NOINLINE int32_t
mul_add_i8_as_i32(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    const hn::ScalableTag<int8_t>  d8;
    const hn::ScalableTag<int32_t> d32;
    const auto kernel_fn = [d32](auto lhs_i8, auto rhs_i8, auto& accu) noexcept {
        accu = hn::SumOfMulQuadAccumulate(d32, lhs_i8, rhs_i8, accu);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), kernel_fn, VecAdd(), LaneReduceSum());
}

HWY_NOINLINE int64_t
my_hwy_i8_dot_product(const int8_t* a, const int8_t* b, size_t sz) VESPA_HWY_NOEXCEPT {
    // TODO have a way to explicitly disable fallbacks for benchmarking purposes
#if HWY_TARGET != HWY_NEON
    // If we cannot possibly overflow intermediate i32 accumulators we can directly
    // compute the dot product without requiring any chunking. Max chunk size is defined
    // by the number of worst-case sums of i8 multiplications (-128**2) that can fit
    // into a single i32 accumulator.
    constexpr size_t max_n_per_chunk = INT32_MAX / (INT8_MIN*INT8_MIN);
    if (sz <= max_n_per_chunk) [[likely]] {
        return mul_add_i8_as_i32(a, b, sz);
    }
    // TODO explicitly test overflow fallback path
    int64_t sum = 0;
    size_t i = 0;
    for (; i + max_n_per_chunk <= sz; i += max_n_per_chunk) {
        sum += mul_add_i8_as_i32(a + i, b + i, max_n_per_chunk);
    }
    if (sz > i) {
        sum += mul_add_i8_as_i32(a + i, b + i, sz - i);
    }
    return sum;
#else
    // The `SumOfMulQuadAccumulate` op seems to have suboptimal codegen for the NEON target
    // on Highway 1.2.0, so here the auto-vectorizer actually wins out by some margin.
    return PlatformGenericAccelerator().dotProduct(a, b, sz);
#endif
}

const char* my_hwy_target_name() VESPA_HWY_NOEXCEPT {
    return hwy::TargetName(HWY_TARGET);
}

#if VESPA_HWY_DYNAMIC
}  // namespace HWY_NAMESPACE
}  // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();
#else
}  // namespace vespalib::hwaccelerated
#endif // VESPA_HWY_DYNAMIC

#if HWY_ONCE

namespace vespalib::hwaccelerated {

#if VESPA_HWY_DYNAMIC

HWY_EXPORT(my_hwy_dot_float);
HWY_EXPORT(my_hwy_dot_bf16);
HWY_EXPORT(my_hwy_dot_double);
HWY_EXPORT(my_hwy_popcount);
HWY_EXPORT(my_hwy_square_euclidean_distance_float_unrolled);
HWY_EXPORT(my_hwy_square_euclidean_distance_double_unrolled);
HWY_EXPORT(my_hwy_square_euclidean_distance_i8);
HWY_EXPORT(my_hwy_bf16_square_euclidean_distance_unrolled);
HWY_EXPORT(my_hwy_i8_dot_product);
HWY_EXPORT(my_hwy_target_name);

float HwyAccelerator::dotProduct(const float* a, const float* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_dot_float)(a, b, sz);
}

float HwyAccelerator::dotProduct(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_dot_bf16)(a, b, sz);
}

double HwyAccelerator::dotProduct(const double* a, const double* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_dot_double)(a, b, sz);
}

int64_t HwyAccelerator::dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept {
    // return N_NEON_BF16::my_hwy_i8_dot_product(a, b, sz); // Mac M1 has dotproduct, but not BF16, but this doesn't use BF16
    return HWY_DYNAMIC_DISPATCH(my_hwy_i8_dot_product)(a, b, sz);
}

size_t HwyAccelerator::populationCount(const uint64_t *a, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_popcount)(a, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_square_euclidean_distance_i8)(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_square_euclidean_distance_float_unrolled)(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_square_euclidean_distance_double_unrolled)(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_bf16_square_euclidean_distance_unrolled)(a, b, sz);
}

const char* HwyAccelerator::target_name() const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_target_name)();
}

#else // if VESPA_HWY_DYNAMIC

// TODO figure out why Highway dot product is faster for shorter vectors (1000), but
//  seemingly _slower_ for longer vectors (4000)... õ_o
float HwyAccelerator::dotProduct(const float* a, const float* b, size_t sz) const noexcept {
    return my_hwy_dot_float(a, b, sz);
}

float HwyAccelerator::dotProduct(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept {
    return my_hwy_dot_bf16(a, b, sz);
}

double HwyAccelerator::dotProduct(const double* a, const double* b, size_t sz) const noexcept {
    return my_hwy_dot_double(a, b, sz);
}

int64_t HwyAccelerator::dotProduct(const int8_t * a, const int8_t* b, size_t sz) const noexcept {
    return my_hwy_i8_dot_product(a, b, sz);
}

size_t HwyAccelerator::populationCount(const uint64_t* a, size_t sz) const noexcept {
    return my_hwy_popcount(a, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept {
    return my_hwy_square_euclidean_distance_i8(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept {
    return my_hwy_square_euclidean_distance_float_unrolled(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept {
    return my_hwy_square_euclidean_distance_double_unrolled(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept {
    return my_hwy_bf16_square_euclidean_distance_unrolled(a, b, sz);
}

const char* HwyAccelerator::target_name() const noexcept {
    return return hwy::TargetName(HWY_TARGET);
}

#endif // VESPA_HWY_DYNAMIC

} // namespace vespalib::hwaccelerated

#endif // HWY_ONCE
