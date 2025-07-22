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

#include <hwy/highway.h>
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

// Accumulator adding by arithmetic sum of two vectors
struct VecAdd {
    HWY_INLINE auto operator()(auto lhs, auto rhs) const noexcept {
        return hn::Add(lhs, rhs);
    }
};

// Accumulator reduction by summing across all vector lanes of `accu`.
struct LaneReduceSum {
    HWY_INLINE auto operator()(auto d0, auto accu) const noexcept {
        return hn::ReduceSum(d0, accu);
    }
};

template <typename T> //requires (hwy::IsFloat<T>() || hwy::IsSame<T, hwy::bfloat16_t>>())
HWY_INLINE T my_hwy_dot_impl(const T* HWY_RESTRICT a, const T* HWY_RESTRICT b, size_t sz) noexcept {
    const hn::ScalableTag<T> d;
    return hwy::ConvertScalarTo<T>(hn::Dot::Compute<0>(d, a, b, sz));
}

HWY_NOINLINE float my_hwy_dot_float(const float* HWY_RESTRICT a, const float* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_dot_impl(a, b, sz);
}

HWY_NOINLINE float my_hwy_dot_bf16(const BFloat16* HWY_RESTRICT a, const BFloat16* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    // Highway already comes with dot product kernels supporting BF16, so use these.
    static_assert(sizeof(BFloat16)  == sizeof(hwy::bfloat16_t));
    static_assert(alignof(BFloat16) == alignof(hwy::bfloat16_t));

    const auto* a_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(a);
    const auto* b_bf16 = reinterpret_cast<const hwy::bfloat16_t*>(b);
    return my_hwy_dot_impl(a_bf16, b_bf16, sz);
}

HWY_NOINLINE double my_hwy_dot_double(const double* HWY_RESTRICT a, const double* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_dot_impl(a, b, sz);
}

template <size_t N>
struct IterNum {
    static constexpr size_t value = N;
};

template <size_t N>
struct FnAccuArity {
    static constexpr size_t value = N;
};

// 4-way accumulator dispatch

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<1>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        accu_fn(vec, accu0);
    } else if (my_idx == 1) {
        accu_fn(vec, accu1);
    } else if (my_idx == 2) {
        accu_fn(vec, accu2);
    } else if (my_idx == 3) {
        accu_fn(vec, accu3);
    }
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<1>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                      AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        accu_fn(lhs, rhs, accu0);
    } else if (my_idx == 1) {
        accu_fn(lhs, rhs, accu1);
    } else if (my_idx == 2) {
        accu_fn(lhs, rhs, accu2);
    } else if (my_idx == 3) {
        accu_fn(lhs, rhs, accu3);
    }
}

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<2>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        accu_fn(vec, accu0, accu1);
    } else if (my_idx == 1) {
        accu_fn(vec, accu2, accu3);
    }
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<2>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        accu_fn(lhs, rhs, accu0, accu1);
    } else if (my_idx == 1) {
        accu_fn(lhs, rhs, accu2, accu3);
    }
}

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<4>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    accu_fn(vec, accu0, accu1, accu2, accu3);
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<4>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    accu_fn(lhs, rhs, accu0, accu1, accu2, accu3);
}

// 8-way accumulator dispatch

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<1>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 8;
    if constexpr (my_idx == 0) {
        accu_fn(vec, accu0);
    } else if (my_idx == 1) {
        accu_fn(vec, accu1);
    } else if (my_idx == 2) {
        accu_fn(vec, accu2);
    } else if (my_idx == 3) {
        accu_fn(vec, accu3);
    } else if (my_idx == 4) {
        accu_fn(vec, accu4);
    } else if (my_idx == 5) {
        accu_fn(vec, accu5);
    } else if (my_idx == 6) {
        accu_fn(vec, accu6);
    } else if (my_idx == 7) {
        accu_fn(vec, accu7);
    }
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<1>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 8;
    if constexpr (my_idx == 0) {
        accu_fn(lhs, rhs, accu0);
    } else if (my_idx == 1) {
        accu_fn(lhs, rhs, accu1);
    } else if (my_idx == 2) {
        accu_fn(lhs, rhs, accu2);
    } else if (my_idx == 3) {
        accu_fn(lhs, rhs, accu3);
    } else if (my_idx == 4) {
        accu_fn(lhs, rhs, accu4);
    } else if (my_idx == 5) {
        accu_fn(lhs, rhs, accu5);
    } else if (my_idx == 6) {
        accu_fn(lhs, rhs, accu6);
    } else if (my_idx == 7) {
        accu_fn(lhs, rhs, accu7);
    }
}

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<2>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        accu_fn(vec, accu0, accu1);
    } else if (my_idx == 1) {
        accu_fn(vec, accu2, accu3);
    } else if (my_idx == 2) {
        accu_fn(vec, accu4, accu5);
    } else if (my_idx == 3) {
        accu_fn(vec, accu6, accu7);
    }
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<2>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        accu_fn(lhs, rhs, accu0, accu1);
    } else if (my_idx == 1) {
        accu_fn(lhs, rhs, accu2, accu3);
    } else if (my_idx == 2) {
        accu_fn(lhs, rhs, accu4, accu5);
    } else if (my_idx == 3) {
        accu_fn(lhs, rhs, accu6, accu7);
    }
}

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<4>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        accu_fn(vec, accu0, accu1, accu2, accu3);
    } else if (my_idx == 1) {
        accu_fn(vec, accu4, accu5, accu6, accu7);
    }
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<4>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        accu_fn(lhs, rhs, accu0, accu1, accu2, accu3);
    } else if (my_idx == 1) {
        accu_fn(lhs, rhs, accu4, accu5, accu6, accu7);
    }
}

template <size_t Idx, typename AccuFnT, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<8>, IterNum<Idx>, AccuFnT&& accu_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    accu_fn(vec, accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
}

template <size_t Idx, typename AccuFnT, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<8>, IterNum<Idx>, AccuFnT&& accu_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    accu_fn(lhs, rhs, accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
}

template <size_t UnrollFactor>
struct UnrolledLoopBody;

template <>
struct UnrolledLoopBody<1> {

    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a,
                                  size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        (void)N;
        const auto a0 = hn::LoadU(d, a + i);
        dispatch(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_pairwise_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        (void)N;
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <>
struct UnrolledLoopBody<2> {
    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a,
                                  size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        dispatch(FnAccuArity<Arity>{}, IterNum<1>{}, accu_fn, a1, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_pairwise_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        const auto b1 = hn::LoadU(d, b + i);
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<1>{}, accu_fn, a1, b1, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <>
struct UnrolledLoopBody<4> {
    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a,
                                  size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<1>{}, accu_fn, a1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<2>{}, accu_fn, a2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        dispatch(FnAccuArity<Arity>{}, IterNum<3>{}, accu_fn, a3, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_pairwise_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        const auto b1 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<1>{}, accu_fn, a1, b1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        const auto b2 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<2>{}, accu_fn, a2, b2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        const auto b3 = hn::LoadU(d, b + i);
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<3>{}, accu_fn, a3, b3, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <>
struct UnrolledLoopBody<8> {
    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a,
                                  size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<1>{}, accu_fn, a1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<2>{}, accu_fn, a2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<3>{}, accu_fn, a3, std::forward<AccuVecs>(accu_vecs)...);
        const auto a4 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<4>{}, accu_fn, a4, std::forward<AccuVecs>(accu_vecs)...);
        const auto a5 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<5>{}, accu_fn, a5, std::forward<AccuVecs>(accu_vecs)...);
        const auto a6 = hn::LoadU(d, a + i);
        i += N;
        dispatch(FnAccuArity<Arity>{}, IterNum<6>{}, accu_fn, a6, std::forward<AccuVecs>(accu_vecs)...);
        const auto a7 = hn::LoadU(d, a + i);
        dispatch(FnAccuArity<Arity>{}, IterNum<7>{}, accu_fn, a7, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename AccuFn, typename... AccuVecs>
    HWY_INLINE
    static void full_pairwise_vector_loads(FnAccuArity<Arity>, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, AccuFn accu_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        const auto b1 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<1>{}, accu_fn, a1, b1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        const auto b2 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<2>{}, accu_fn, a2, b2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        const auto b3 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<3>{}, accu_fn, a3, b3, std::forward<AccuVecs>(accu_vecs)...);
        const auto a4 = hn::LoadU(d, a + i);
        const auto b4 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<4>{}, accu_fn, a4, b4, std::forward<AccuVecs>(accu_vecs)...);
        const auto a5 = hn::LoadU(d, a + i);
        const auto b5 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<5>{}, accu_fn, a5, b5, std::forward<AccuVecs>(accu_vecs)...);
        const auto a6 = hn::LoadU(d, a + i);
        const auto b6 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<6>{}, accu_fn, a6, b6, std::forward<AccuVecs>(accu_vecs)...);
        const auto a7 = hn::LoadU(d, a + i);
        const auto b7 = hn::LoadU(d, b + i);
        dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<7>{}, accu_fn, a7, b7, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <size_t N>
struct UnrolledBy {
    constexpr static size_t unrolled_by_v = N;
};

template <size_t N>
struct UsesNAccumulators {
    constexpr static size_t uses_n_accumulators_v = N;
};

template <size_t N>
struct HasAccumulatorArity {
    constexpr static size_t fn_has_accu_arity_v = N;
};

// TODO replace with concept constraints instead?
//  For now, use distinct value names to cause compilation errors on wrong arg ordering
template <
    typename UsesNAccumulatorsT,
    typename UnrolledByT,
    typename FnHasAccuArityT
>
struct HwyReduceKernel;

template <typename UnrolledByT, typename FnHasAccuArityT>
struct HwyReduceKernel<UsesNAccumulators<4>, UnrolledByT, FnHasAccuArityT> {
    static constexpr size_t AccumulatorCount = 4;
    static constexpr size_t UnrollFactor = UnrolledByT::unrolled_by_v;
    static constexpr size_t Arity = FnHasAccuArityT::fn_has_accu_arity_v;

    // TODO dedupe core kernel code...
    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R elementwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a,
            size_t n_elems,
            const hn::Vec<DA> init_accu,
            const AccuFn accu_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        const size_t N = hn::Lanes(d);
        // Vector types may be _sizeless_ on some archs (SVE, RVV) so we can't use arrays.
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;

        size_t i = 0;
        for (; (i + UnrollFactor*N) <= n_elems; i += UnrollFactor*N) {
            UnrolledLoopBody<UnrollFactor>::full_vector_loads(FnAccuArity<Arity>{}, d, a, i, N, accu_fn,
                                                              accu0, accu1, accu2, accu3);
        }
        // Boundary case: up to (and including) UnrollFactor-1 whole vectors at the end
        for (; (i + N) <= n_elems; i += N) {
            UnrolledLoopBody<1>::full_vector_loads(FnAccuArity<Arity>{}, d, a, i, N, accu_fn,
                                                   accu0, accu1, accu2, accu3);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            dispatch(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, accu0, accu1, accu2, accu3);
        }
        // Reduction tree:
        //  0 + 1 => 0, 2 + 3 => 2
        //  0 + 2 => 0
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu0 = accu_reducer_fn(accu0, accu2);
        return lane_reducer_fn(da, accu0);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R pairwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            size_t n_elems,
            const hn::Vec<DA> init_accu,
            const AccuFn accu_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        const size_t N = hn::Lanes(d);
        // Vector types may be _sizeless_ on some archs (SVE, RVV) so we can't use arrays.
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;

        size_t i = 0;
        for (; (i + UnrollFactor*N) <= n_elems; i += UnrollFactor*N) {
            UnrolledLoopBody<UnrollFactor>::full_pairwise_vector_loads(FnAccuArity<Arity>{}, d, a, b, i, N, accu_fn,
                                                                       accu0, accu1, accu2, accu3);
        }
        // Boundary case: up to (and including) UnrollFactor-1 whole vectors at the end
        for (; (i + N) <= n_elems; i += N) {
            UnrolledLoopBody<1>::full_pairwise_vector_loads(FnAccuArity<Arity>{}, d, a, b, i, N, accu_fn,
                                                            accu0, accu1, accu2, accu3);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            const auto b0 = hn::LoadN(d, b + i, rem);
            dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, b0, accu0, accu1, accu2, accu3);
        }
        // Reduction tree:
        //  0 + 1 => 0, 2 + 3 => 2
        //  0 + 2 => 0
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu0 = accu_reducer_fn(accu0, accu2);
        return lane_reducer_fn(da, accu0);
    }
};

template <typename UnrolledByT, typename FnHasAccuArityT>
struct HwyReduceKernel<UsesNAccumulators<8>, UnrolledByT, FnHasAccuArityT> {
    static constexpr size_t AccumulatorCount = 8;
    static constexpr size_t UnrollFactor = UnrolledByT::unrolled_by_v;
    static constexpr size_t Arity = FnHasAccuArityT::fn_has_accu_arity_v;

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R elementwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a,
            size_t n_elems,
            const hn::Vec<DA> init_accu,
            const AccuFn accu_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        const size_t N = hn::Lanes(d);
        // Vector types may be _sizeless_ on some archs (SVE, RVV) so we can't use arrays.
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        AccuV accu4 = init_accu;
        AccuV accu5 = init_accu;
        AccuV accu6 = init_accu;
        AccuV accu7 = init_accu;

        size_t i = 0;
        for (; (i + UnrollFactor*N) <= n_elems; i += UnrollFactor*N) {
            UnrolledLoopBody<UnrollFactor>::full_vector_loads(FnAccuArity<Arity>{}, d, a, i, N, accu_fn,
                                                              accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        }
        // Boundary case: up to (and including) UnrollFactor-1 whole vectors at the end
        for (; (i + N) <= n_elems; i += N) {
            UnrolledLoopBody<1>::full_vector_loads(FnAccuArity<Arity>{}, d, a, i, N, accu_fn,
                                                   accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            dispatch(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0,
                     accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        }
        // Reduction tree:
        //  first 0+1 => 0, 2+3 => 2, 4+5 => 4, 6+7 => 6
        //  then  0+2 => 0, 4+6 => 4
        //  then  0+4 => 0
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu4 = accu_reducer_fn(accu4, accu5);
        accu6 = accu_reducer_fn(accu6, accu7);

        accu0 = accu_reducer_fn(accu0, accu2);
        accu4 = accu_reducer_fn(accu4, accu6);

        accu0 = accu_reducer_fn(accu0, accu4);
        return lane_reducer_fn(da, accu0);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R pairwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            size_t n_elems,
            const hn::Vec<DA> init_accu,
            const AccuFn accu_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        const size_t N = hn::Lanes(d);
        // Vector types may be _sizeless_ on some archs (SVE, RVV) so we can't use arrays.
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        AccuV accu4 = init_accu;
        AccuV accu5 = init_accu;
        AccuV accu6 = init_accu;
        AccuV accu7 = init_accu;

        size_t i = 0;
        for (; (i + UnrollFactor*N) <= n_elems; i += UnrollFactor*N) {
            UnrolledLoopBody<UnrollFactor>::full_pairwise_vector_loads(FnAccuArity<Arity>{}, d, a, b, i, N, accu_fn,
                                                                       accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        }
        // Boundary case: up to (and including) UnrollFactor-1 whole vectors at the end
        for (; (i + N) <= n_elems; i += N) {
            UnrolledLoopBody<1>::full_pairwise_vector_loads(FnAccuArity<Arity>{}, d, a, b, i, N, accu_fn,
                                                            accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            const auto b0 = hn::LoadN(d, b + i, rem);
            dispatch_pairwise(FnAccuArity<Arity>{}, IterNum<0>{}, accu_fn, a0, b0,
                              accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        }
        // Reduction tree:
        //  first 0+1 => 0, 2+3 => 2, 4+5 => 4, 6+7 => 6
        //  then  0+2 => 0, 4+6 => 4
        //  then  0+4 => 0
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu4 = accu_reducer_fn(accu4, accu5);
        accu6 = accu_reducer_fn(accu6, accu7);

        accu0 = accu_reducer_fn(accu0, accu2);
        accu4 = accu_reducer_fn(accu4, accu6);

        accu0 = accu_reducer_fn(accu0, accu4);
        return lane_reducer_fn(da, accu0);
    }
};

template <typename T> requires (hwy::IsFloat<T>())
HWY_NOINLINE double
my_hwy_square_euclidean_distance_unrolled_impl(const T* a, const T* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<T> d;
    const auto accu_fn = [](auto lhs, auto rhs, auto& accu) noexcept {
        const auto sub = hn::Sub(lhs, rhs);
        accu = hn::MulAdd(sub, sub, accu); // note: using fused multiply-add
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d, d, a, b, sz, hn::Zero(d), accu_fn, VecAdd(), LaneReduceSum());
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
// TODO __attribute__((noclone)) to avoid GCC cloning out a massive blob of AVX-512 code to
//  process constant 256 bytes in one go? Needs to be benchmarked first...
template <typename D>
HWY_NOINLINE int32_t
sub_mul_add_i8s_via_i16_to_i32(D d8, const int8_t* a, const int8_t* b, size_t sz) {
    const hn::Repartition<int16_t, decltype(d8)>  d16;
    const hn::Repartition<int32_t, decltype(d16)> d32;

    auto accu = [d16, d32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) noexcept {
        const auto sub_l_i16 = hn::Sub(hn::PromoteLowerTo(d16, lhs), hn::PromoteLowerTo(d16, rhs));
        const auto sub_u_i16 = hn::Sub(hn::PromoteUpperTo(d16, lhs), hn::PromoteUpperTo(d16, rhs));
        acc0 = hn::ReorderWidenMulAccumulate(d32, sub_l_i16, sub_l_i16, acc0, acc1);
        acc2 = hn::ReorderWidenMulAccumulate(d32, sub_u_i16, sub_u_i16, acc2, acc3);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<4>, UnrolledBy<2>, HasAccumulatorArity<4>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), accu, VecAdd(), LaneReduceSum());
}

HWY_NOINLINE double
my_hwy_square_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<int8_t> d8;
    // Process input in chunks that are small enough that the intermediate i32 accumulators
    // won't overflow, but large enough that we can spin up the vector steam engines fully.
    constexpr size_t loop_count = 256;
    double sum = 0;
    size_t i = 0;
    for (; i + loop_count <= sz; i += loop_count) {
        sum += sub_mul_add_i8s_via_i16_to_i32(d8, a + i, b + i, loop_count);
    }
    if (sz > i) {
        sum += sub_mul_add_i8s_via_i16_to_i32(d8, a + i, b + i, sz - i);
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
#if HWY_TARGET != HWY_AVX2 && HWY_TARGET != HWY_AVX3
    const hn::ScalableTag<uint64_t> d;
    const auto accu_fn = [](auto v, auto& accu) noexcept {
        accu = hn::Add(hn::PopulationCount(v), accu);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::elementwise(d, d, a, sz, hn::Zero(d), accu_fn, VecAdd(), LaneReduceSum());
#else
    // AVX2 and AVX3 do not have dedicated vector popcount instructions, so the Highway "emulation"
    // ends up being slower in practice than the baseline one using 4x pipelined POPCNT.
    return PlatformGenericAccelerator().populationCount(a, sz);
#endif
}

// Perhaps paradoxically, using `noinline` here can result in better codegen since the compiler
// can clone out a distinct function specialization that is completely unrolled for a particular
// iteration count (e.g. 256).
template <typename D8>
HWY_NOINLINE int32_t
mul_add_i8_as_i32(D8 d8, const int8_t* a, const int8_t* b, size_t sz) noexcept {
    const hn::ScalableTag<int32_t> d32;
    const auto accu_fn = [d32](auto lhs_i8, auto rhs_i8, auto& accu) noexcept {
        // FIXME Highway does _not_ generate an SDOT instruction on NEON+dotproduct for i8->i32,
        //  only for NEON+BF16 (since there is no distinct target for just dotproduct), which means
        //  this is ~4x slower than it could ideally be on a MacBook Pro M1...!
        accu = hn::SumOfMulQuadAccumulate(d32, lhs_i8, rhs_i8, accu);
    };
    using MyKernel = HwyReduceKernel<UsesNAccumulators<8>, UnrolledBy<8>, HasAccumulatorArity<1>>;
    return MyKernel::pairwise(d8, d32, a, b, sz, hn::Zero(d32), accu_fn, VecAdd(), LaneReduceSum());
}

HWY_NOINLINE int64_t
my_hwy_i8_dot_product(const int8_t* a, const int8_t* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<int8_t> d8;
    // Process input in chunks that are small enough that the intermediate i32 accumulators
    // won't overflow, but large enough that we can spin up the vector steam engines fully.
    constexpr size_t loop_count = 256;
    int64_t sum = 0;
    size_t i = 0;
    for (; i + loop_count <= sz; i += loop_count) {
        sum += mul_add_i8_as_i32(d8, a + i, b + i, loop_count);
    }
    if (sz > i) [[unlikely]] {
        sum += mul_add_i8_as_i32(d8, a + i, b + i, sz - i);
    }
    return sum;
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
