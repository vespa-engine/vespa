// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// No include guard; this is intentional as this file will be included multiple times by
// the same translation unit as part of compiling for multiple distinct Highway targets.

#include <hwy/highway.h>

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

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

// The counter for the current intra-loop trip counter in an unrolled loop body.
// E.g. for a loop with an unroll factor of 4, the dispatcher function will
// be instantiated with IterNum<N> with N in {0, 1, 2, 3}.
template <size_t N>
struct IterNum {
    static constexpr size_t value = N;
};

// The number of accumulators that a kernel function should be invoked with, i.e.
// its accumulator arity.
template <size_t N>
struct FnAccuArity {
    static constexpr size_t value = N;
};

// We (partially) decouple accumulator parallelism, the unrolling factor and
// how many accumulators a given kernel function uses. "Partially" is because
// we inherently can't use a kernel function requiring _more_ accumulators than
// there are accumulators present, and if we use an 1-ary kernel function with
// an unroll factor of 2 and 4 parallel accumulators, the unrolled loop won't
// be able to use all available accumulators (can only use 2).

// The basic idea is that we want to evenly distribute accumulators across
// kernel function invocations in order to "maximize" the distance between
// definitions and usages of a given accumulator. This is to avoid stalling the
// CPU pipeline by having to wait for in-flight instructions to settle the
// next time the accumulator is loaded.
//
// We do this by constexpr-"striping" accumulator references based on which
// iteration in the unrolled loop body we're currently at.
//
// For example, with 8x unrolling, 4x accumulators and an 1-ary kernel, the
// loop body will be:
//   fn(a0), fn(a1), fn(a2), fn(a3), fn(a0), fn(a1), fn(a2), fn(a3).
// Similarly, with a 2-ary kernel:
//   fn(a0, a1), fn(a2, a3), fn(a0, a1), fn(a2, a3), ...
// Similarly, with a 4-ary kernel:
//   fn(a0, a1, a2, a3), fn(a0, a1, a2, a3), ...
// And so on.

// 4-way accumulator dispatch

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<1>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        kernel_fn(vec, accu0);
    } else if (my_idx == 1) {
        kernel_fn(vec, accu1);
    } else if (my_idx == 2) {
        kernel_fn(vec, accu2);
    } else if (my_idx == 3) {
        kernel_fn(vec, accu3);
    }
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<1>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        kernel_fn(lhs, rhs, accu0);
    } else if (my_idx == 1) {
        kernel_fn(lhs, rhs, accu1);
    } else if (my_idx == 2) {
        kernel_fn(lhs, rhs, accu2);
    } else if (my_idx == 3) {
        kernel_fn(lhs, rhs, accu3);
    }
}

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<2>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        kernel_fn(vec, accu0, accu1);
    } else if (my_idx == 1) {
        kernel_fn(vec, accu2, accu3);
    }
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<2>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        kernel_fn(lhs, rhs, accu0, accu1);
    } else if (my_idx == 1) {
        kernel_fn(lhs, rhs, accu2, accu3);
    }
}

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<4>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    kernel_fn(vec, accu0, accu1, accu2, accu3);
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<4>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3) noexcept
{
    kernel_fn(lhs, rhs, accu0, accu1, accu2, accu3);
}

// 8-way accumulator dispatch

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<1>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 8;
    if constexpr (my_idx == 0) {
        kernel_fn(vec, accu0);
    } else if (my_idx == 1) {
        kernel_fn(vec, accu1);
    } else if (my_idx == 2) {
        kernel_fn(vec, accu2);
    } else if (my_idx == 3) {
        kernel_fn(vec, accu3);
    } else if (my_idx == 4) {
        kernel_fn(vec, accu4);
    } else if (my_idx == 5) {
        kernel_fn(vec, accu5);
    } else if (my_idx == 6) {
        kernel_fn(vec, accu6);
    } else if (my_idx == 7) {
        kernel_fn(vec, accu7);
    }
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<1>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 8;
    if constexpr (my_idx == 0) {
        kernel_fn(lhs, rhs, accu0);
    } else if (my_idx == 1) {
        kernel_fn(lhs, rhs, accu1);
    } else if (my_idx == 2) {
        kernel_fn(lhs, rhs, accu2);
    } else if (my_idx == 3) {
        kernel_fn(lhs, rhs, accu3);
    } else if (my_idx == 4) {
        kernel_fn(lhs, rhs, accu4);
    } else if (my_idx == 5) {
        kernel_fn(lhs, rhs, accu5);
    } else if (my_idx == 6) {
        kernel_fn(lhs, rhs, accu6);
    } else if (my_idx == 7) {
        kernel_fn(lhs, rhs, accu7);
    }
}

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<2>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        kernel_fn(vec, accu0, accu1);
    } else if (my_idx == 1) {
        kernel_fn(vec, accu2, accu3);
    } else if (my_idx == 2) {
        kernel_fn(vec, accu4, accu5);
    } else if (my_idx == 3) {
        kernel_fn(vec, accu6, accu7);
    }
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<2>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 4;
    if constexpr (my_idx == 0) {
        kernel_fn(lhs, rhs, accu0, accu1);
    } else if (my_idx == 1) {
        kernel_fn(lhs, rhs, accu2, accu3);
    } else if (my_idx == 2) {
        kernel_fn(lhs, rhs, accu4, accu5);
    } else if (my_idx == 3) {
        kernel_fn(lhs, rhs, accu6, accu7);
    }
}

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<4>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        kernel_fn(vec, accu0, accu1, accu2, accu3);
    } else if (my_idx == 1) {
        kernel_fn(vec, accu4, accu5, accu6, accu7);
    }
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<4>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    constexpr size_t my_idx = Idx % 2;
    if constexpr (my_idx == 0) {
        kernel_fn(lhs, rhs, accu0, accu1, accu2, accu3);
    } else if (my_idx == 1) {
        kernel_fn(lhs, rhs, accu4, accu5, accu6, accu7);
    }
}

template <size_t Idx, typename KernelFn, typename VecT, typename AccuV>
HWY_INLINE
void dispatch(FnAccuArity<8>, IterNum<Idx>, KernelFn&& kernel_fn, VecT vec,
              AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
              AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    kernel_fn(vec, accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
}

template <size_t Idx, typename KernelFn, typename LhsT, typename RhsT, typename AccuV>
HWY_INLINE
void dispatch_pairwise(FnAccuArity<8>, IterNum<Idx>, KernelFn&& kernel_fn, LhsT lhs, RhsT rhs,
                       AccuV& accu0, AccuV& accu1, AccuV& accu2, AccuV& accu3,
                       AccuV& accu4, AccuV& accu5, AccuV& accu6, AccuV& accu7) noexcept
{
    kernel_fn(lhs, rhs, accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
}

// To avoid the need for any compiler pragma alchemy to achieve a desired loop unrolling
// factor, we explicitly unroll loop bodies by specializing distinct implementations of
// the loop bodies. We manually increment the loop induction variable between each kernel
// function dispatch; the compiler happily optimizes this away.
// Each unrolled dispatch within a loop body is provided with an IterNum<N> that corresponds
// to the unroll trip counter. This can then be used by the dispatcher function to choose
// which accumulator to use for this particular instantiation and kernel accumulator arity.

template <size_t UnrollFactor>
struct UnrolledLoopBody;

template <>
struct UnrolledLoopBody<1> {
    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void elementwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a,
                                              size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        (void)N;
        const auto a0 = hn::LoadU(d, a + i);
        dispatch(arity, IterNum<0>{}, kernel_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void pairwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        (void)N;
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        dispatch_pairwise(arity, IterNum<0>{}, kernel_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <>
struct UnrolledLoopBody<2> {
    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void elementwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a,
                                              size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<0>{}, kernel_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        dispatch(arity, IterNum<1>{}, kernel_fn, a1, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void pairwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<0>{}, kernel_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        const auto b1 = hn::LoadU(d, b + i);
        dispatch_pairwise(arity, IterNum<1>{}, kernel_fn, a1, b1, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <>
struct UnrolledLoopBody<4> {
    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void elementwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a,
                                              size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<0>{}, kernel_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<1>{}, kernel_fn, a1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<2>{}, kernel_fn, a2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        dispatch(arity, IterNum<3>{}, kernel_fn, a3, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void pairwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<0>{}, kernel_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        const auto b1 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<1>{}, kernel_fn, a1, b1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        const auto b2 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<2>{}, kernel_fn, a2, b2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        const auto b3 = hn::LoadU(d, b + i);
        dispatch_pairwise(arity, IterNum<3>{}, kernel_fn, a3, b3, std::forward<AccuVecs>(accu_vecs)...);
    }
};

template <>
struct UnrolledLoopBody<8> {
    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void elementwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a,
                                              size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<0>{}, kernel_fn, a0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<1>{}, kernel_fn, a1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<2>{}, kernel_fn, a2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<3>{}, kernel_fn, a3, std::forward<AccuVecs>(accu_vecs)...);
        const auto a4 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<4>{}, kernel_fn, a4, std::forward<AccuVecs>(accu_vecs)...);
        const auto a5 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<5>{}, kernel_fn, a5, std::forward<AccuVecs>(accu_vecs)...);
        const auto a6 = hn::LoadU(d, a + i);
        i += N;
        dispatch(arity, IterNum<6>{}, kernel_fn, a6, std::forward<AccuVecs>(accu_vecs)...);
        const auto a7 = hn::LoadU(d, a + i);
        dispatch(arity, IterNum<7>{}, kernel_fn, a7, std::forward<AccuVecs>(accu_vecs)...);
    }

    template <size_t Arity, typename D, typename T, typename KernelFn, typename... AccuVecs>
    HWY_INLINE
    static void pairwise_load_and_dispatch(const FnAccuArity<Arity> arity, D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
                                           size_t i, const size_t N, KernelFn kernel_fn, AccuVecs&&... accu_vecs) noexcept
    {
        const auto a0 = hn::LoadU(d, a + i);
        const auto b0 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<0>{}, kernel_fn, a0, b0, std::forward<AccuVecs>(accu_vecs)...);
        const auto a1 = hn::LoadU(d, a + i);
        const auto b1 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<1>{}, kernel_fn, a1, b1, std::forward<AccuVecs>(accu_vecs)...);
        const auto a2 = hn::LoadU(d, a + i);
        const auto b2 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<2>{}, kernel_fn, a2, b2, std::forward<AccuVecs>(accu_vecs)...);
        const auto a3 = hn::LoadU(d, a + i);
        const auto b3 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<3>{}, kernel_fn, a3, b3, std::forward<AccuVecs>(accu_vecs)...);
        const auto a4 = hn::LoadU(d, a + i);
        const auto b4 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<4>{}, kernel_fn, a4, b4, std::forward<AccuVecs>(accu_vecs)...);
        const auto a5 = hn::LoadU(d, a + i);
        const auto b5 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<5>{}, kernel_fn, a5, b5, std::forward<AccuVecs>(accu_vecs)...);
        const auto a6 = hn::LoadU(d, a + i);
        const auto b6 = hn::LoadU(d, b + i);
        i += N;
        dispatch_pairwise(arity, IterNum<6>{}, kernel_fn, a6, b6, std::forward<AccuVecs>(accu_vecs)...);
        const auto a7 = hn::LoadU(d, a + i);
        const auto b7 = hn::LoadU(d, b + i);
        dispatch_pairwise(arity, IterNum<7>{}, kernel_fn, a7, b7, std::forward<AccuVecs>(accu_vecs)...);
    }
};

// The kernel body wraps all needed boundary condition handling for vectorized loops.
// For all input blocks that are a multiple of the vector size*unroll factor we run
// the main vector steam engine loop block. This is where most of the work is expected
// to be done, and is where accumulator and instruction parallelism will mean the most.
// We then process any whole vectors that are remaining (for blocks that are a multiple
// of the vector size), before any final remaining elements are processed.
// Important: we handle elements _outside_ the boundary by making them implicitly zero!
// This means the kernel function MUST treat zero-elements the same "as if" the elements
// did not exist in the first place. For most distance functions this is implicitly the
// case since the contribution of lhs 0 vs rhs 0 is also 0.

template <size_t UnrollFactor>
struct KernelBody {
    template <
        size_t Arity,
        typename D,
        typename T = hn::TFromD<D>,
        typename KernelFn,
        typename... Accumulators
    >
    HWY_INLINE
    static void pairwise_body_impl(
            const FnAccuArity<Arity> arity,
            const D d,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            const size_t n_elems,
            const KernelFn kernel_fn,
            Accumulators&&... accumulators) noexcept
    {
        HWY_LANES_CONSTEXPR const size_t N = hn::Lanes(d);
        size_t i = 0;
        for (; (i + UnrollFactor*N) <= n_elems; i += UnrollFactor*N) {
            UnrolledLoopBody<UnrollFactor>::pairwise_load_and_dispatch(arity, d, a, b, i, N, kernel_fn,
                                                                       std::forward<Accumulators>(accumulators)...);
        }
        // Boundary case: up to (and including) UnrollFactor-1 whole vectors at the end
        for (; (i + N) <= n_elems; i += N) {
            UnrolledLoopBody<1>::pairwise_load_and_dispatch(arity, d, a, b, i, N, kernel_fn,
                                                            std::forward<Accumulators>(accumulators)...);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            const auto b0 = hn::LoadN(d, b + i, rem);
            dispatch_pairwise(arity, IterNum<0>{}, kernel_fn, a0, b0,
                              std::forward<Accumulators>(accumulators)...);
        }
    }

    template <
        size_t Arity,
        typename D,
        typename T = hn::TFromD<D>,
        typename KernelFn,
        typename... Accumulators
    >
    HWY_INLINE
    static void elementwise_body_impl(
            const FnAccuArity<Arity> arity,
            const D d,
            const T* HWY_RESTRICT a,
            const size_t n_elems,
            const KernelFn kernel_fn,
            Accumulators&&... accumulators) noexcept
    {
        HWY_LANES_CONSTEXPR const size_t N = hn::Lanes(d);
        size_t i = 0;
        for (; (i + UnrollFactor*N) <= n_elems; i += UnrollFactor*N) {
            UnrolledLoopBody<UnrollFactor>::elementwise_load_and_dispatch(arity, d, a, i, N, kernel_fn,
                                                                          std::forward<Accumulators>(accumulators)...);
        }
        // Boundary case: up to (and including) UnrollFactor-1 whole vectors at the end
        for (; (i + N) <= n_elems; i += N) {
            UnrolledLoopBody<1>::elementwise_load_and_dispatch(arity, d, a, i, N, kernel_fn,
                                                               std::forward<Accumulators>(accumulators)...);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            dispatch(arity, IterNum<0>{}, kernel_fn, a0,
                     std::forward<Accumulators>(accumulators)...);
        }
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

// Ideally we would be able to make our lives easier by representing N parallel accumulators
// with having a vector array of size N. Unfortunately, vector types may be _sizeless_ on
// some archs (SVE, RVV) so arrays (and even class members) are out of the question. Instead,
// specialize a set of HwyReduceKernel implementations on the accumulator parallelism factor
// and use N distinct, named accumulator variables. These can then be forwarded to more
// generalized variadic templated functions.

template <typename UnrolledByT, typename FnHasAccuArityT>
struct HwyReduceKernel<UsesNAccumulators<4>, UnrolledByT, FnHasAccuArityT> {
    static constexpr size_t AccumulatorCount = 4;
    static constexpr size_t UnrollFactor = UnrolledByT::unrolled_by_v;
    static constexpr size_t Arity = FnHasAccuArityT::fn_has_accu_arity_v;

    template <typename AccuReducerFn, typename AccuV>
    HWY_INLINE
    static AccuV parallel_reduce_accumulators(AccuReducerFn accu_reducer_fn,
                                              AccuV accu0, AccuV accu1, AccuV accu2, AccuV accu3) noexcept
    {
        // 4-way reduction tree:
        //  0 + 1 => 0, 2 + 3 => 2
        //  0 + 2 => result
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        return accu_reducer_fn(accu0, accu2);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename KernelFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R elementwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a,
            const size_t n_elems,
            const hn::Vec<DA> init_accu,
            const KernelFn kernel_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        KernelBody<UnrollFactor>::elementwise_body_impl(FnAccuArity<Arity>{}, d, a, n_elems, kernel_fn,
                                                        accu0, accu1, accu2, accu3);
        AccuV reduced = parallel_reduce_accumulators(accu_reducer_fn, accu0, accu1, accu2, accu3);
        return lane_reducer_fn(da, reduced);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename KernelFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R pairwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            const size_t n_elems,
            const hn::Vec<DA> init_accu,
            const KernelFn kernel_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        KernelBody<UnrollFactor>::pairwise_body_impl(FnAccuArity<Arity>{}, d, a, b, n_elems, kernel_fn,
                                                     accu0, accu1, accu2, accu3);
        AccuV reduced = parallel_reduce_accumulators(accu_reducer_fn, accu0, accu1, accu2, accu3);
        return lane_reducer_fn(da, reduced);
    }
};

template <typename UnrolledByT, typename FnHasAccuArityT>
struct HwyReduceKernel<UsesNAccumulators<8>, UnrolledByT, FnHasAccuArityT> {
    static constexpr size_t AccumulatorCount = 8;
    static constexpr size_t UnrollFactor = UnrolledByT::unrolled_by_v;
    static constexpr size_t Arity = FnHasAccuArityT::fn_has_accu_arity_v;

    template <typename AccuReducerFn, typename AccuV>
    HWY_INLINE
    static AccuV parallel_reduce_accumulators(AccuReducerFn accu_reducer_fn,
                                              AccuV accu0, AccuV accu1, AccuV accu2, AccuV accu3,
                                              AccuV accu4, AccuV accu5, AccuV accu6, AccuV accu7) noexcept
    {
        // 8-way reduction tree:
        //  first 0+1 => 0, 2+3 => 2, 4+5 => 4, 6+7 => 6
        //  then  0+2 => 0, 4+6 => 4
        //  then  0+4 => result
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu4 = accu_reducer_fn(accu4, accu5);
        accu6 = accu_reducer_fn(accu6, accu7);

        accu0 = accu_reducer_fn(accu0, accu2);
        accu4 = accu_reducer_fn(accu4, accu6);

        return accu_reducer_fn(accu0, accu4);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename KernelFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R elementwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a,
            const size_t n_elems,
            const hn::Vec<DA> init_accu,
            const KernelFn kernel_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        AccuV accu4 = init_accu;
        AccuV accu5 = init_accu;
        AccuV accu6 = init_accu;
        AccuV accu7 = init_accu;
        KernelBody<UnrollFactor>::elementwise_body_impl(FnAccuArity<Arity>{}, d, a, n_elems, kernel_fn,
                                                        accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        AccuV reduced = parallel_reduce_accumulators(accu_reducer_fn, accu0, accu1, accu2,
                                                     accu3, accu4, accu5, accu6, accu7);
        return lane_reducer_fn(da, reduced);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename KernelFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    [[nodiscard]] static R pairwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            const size_t n_elems,
            const hn::Vec<DA> init_accu,
            const KernelFn kernel_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        AccuV accu4 = init_accu;
        AccuV accu5 = init_accu;
        AccuV accu6 = init_accu;
        AccuV accu7 = init_accu;
        KernelBody<UnrollFactor>::pairwise_body_impl(FnAccuArity<Arity>{}, d, a, b, n_elems, kernel_fn,
                                                     accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
        AccuV reduced = parallel_reduce_accumulators(accu_reducer_fn, accu0, accu1, accu2,
                                                     accu3, accu4, accu5, accu6, accu7);
        return lane_reducer_fn(da, reduced);
    }
};

// Utility function for invoking a function that has an intermediate result type that
// may overflow if the input size is beyond a certain threshold. If the size is > this
// threshold, invoke the function on input chunks that do not exceed this threshold,
// maintaining a running sum across the chunks. The sum type must be one that is _not_
// expected to overflow regardless of the input size.
template <size_t MaxChunkSize, typename SumT, typename F, typename T>
[[nodiscard]] SumT
compute_chunked_sum(F&& fn, const T* HWY_RESTRICT lhs, const T* HWY_RESTRICT rhs, const size_t sz) noexcept {
    if (sz <= MaxChunkSize) [[likely]] {
        return fn(lhs, rhs, sz);
    }
    // Process input in chunks that are small enough that the intermediate accumulators
    // won't overflow, but large enough that we can spin up the vector steam engines fully.
    SumT sum{};
    size_t i = 0;
    for (; i + MaxChunkSize <= sz; i += MaxChunkSize) {
        sum += fn(lhs + i, rhs + i, MaxChunkSize);
    }
    if (sz > i) {
        sum += fn(lhs + i, rhs + i, sz - i);
    }
    return sum;
}


}  // namespace HWY_NAMESPACE
}  // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();
