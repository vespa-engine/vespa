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
#include <hwy/print-inl.h> // TODO temp

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

template <typename Derived>
struct UnrollBase {
    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn
    >
    HWY_INLINE static R reduce_pairwise_with_sum(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            size_t n_elems,
            const AccuFn accu_fn) noexcept
    {
        return Derived::reduce_pairwise(d, da, a, b, n_elems, hn::Zero(da), accu_fn, VecAdd(), LaneReduceSum());
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn
    >
    HWY_INLINE static R reduce_with_sum(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a,
            size_t n_elems,
            const AccuFn accu_fn) noexcept
    {
        return Derived::reduce(d, da, a, n_elems, hn::Zero(da), accu_fn, VecAdd(), LaneReduceSum());
    }
};

enum UnrollAssumptions {
    MultipleOfVector = 1
};

template <int Assumptions = 0>
struct Unroll8X : UnrollBase<Unroll8X<Assumptions>> {
    template<
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    static R reduce_pairwise(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            size_t n_elems,
            const hn::Vec<DA> init_accu,
            const AccuFn accu_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept {
        using AccuV = hn::Vec<DA>;
        const size_t N = hn::Lanes(d);
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        AccuV accu4 = init_accu;
        AccuV accu5 = init_accu;
        AccuV accu6 = init_accu;
        AccuV accu7 = init_accu;
        size_t i = 0;

        for (; (i + 8 * N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            i += N;
            accu0 = accu_fn(accu0, a0, b0);
            const auto a1 = hn::LoadU(d, a + i);
            const auto b1 = hn::LoadU(d, b + i);
            i += N;
            accu1 = accu_fn(accu1, a1, b1);
            const auto a2 = hn::LoadU(d, a + i);
            const auto b2 = hn::LoadU(d, b + i);
            i += N;
            accu2 = accu_fn(accu2, a2, b2);
            const auto a3 = hn::LoadU(d, a + i);
            const auto b3 = hn::LoadU(d, b + i);
            i += N;
            accu3 = accu_fn(accu3, a3, b3);
            const auto a4 = hn::LoadU(d, a + i);
            const auto b4 = hn::LoadU(d, b + i);
            i += N;
            accu4 = accu_fn(accu4, a4, b4);
            const auto a5 = hn::LoadU(d, a + i);
            const auto b5 = hn::LoadU(d, b + i);
            i += N;
            accu5 = accu_fn(accu5, a5, b5);
            const auto a6 = hn::LoadU(d, a + i);
            const auto b6 = hn::LoadU(d, b + i);
            i += N;
            accu6 = accu_fn(accu6, a6, b6);
            const auto a7 = hn::LoadU(d, a + i);
            const auto b7 = hn::LoadU(d, b + i);
            i += N;
            accu7 = accu_fn(accu7, a7, b7);
        }
        constexpr bool is_multiple_of_vec = (Assumptions & UnrollAssumptions::MultipleOfVector) != 0;
        if constexpr (!is_multiple_of_vec) {
            // Boundary case: up to (and including) 7 whole vectors at the end,
            // but not a full (or possibly any) 8th vector.
            for (; (i + N) <= n_elems; i += N) {
                const auto a0 = hn::LoadU(d, a + i);
                const auto b0 = hn::LoadU(d, b + i);
                accu0 = accu_fn(accu0, a0, b0);
            }
            // Process up any final stragglers of < N elems
            const size_t rem = n_elems - i;
            if (rem != 0) {
                // Lanes OOB will be _zero_
                const auto a0 = hn::LoadN(d, a + i, rem);
                const auto b0 = hn::LoadN(d, b + i, rem);
                accu1 = accu_fn(accu1, a0, b0);
            }
        } else {
            HWY_DASSERT(i == n_elems);
        }
        // Reduce accumulators in parallel, then reduce down to final:
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
    static R reduce(
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
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        AccuV accu4 = init_accu;
        AccuV accu5 = init_accu;
        AccuV accu6 = init_accu;
        AccuV accu7 = init_accu;
        size_t i = 0;

        for (; (i + 8 * N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            i += N;
            accu0 = accu_fn(accu0, a0);
            const auto a1 = hn::LoadU(d, a + i);
            i += N;
            accu1 = accu_fn(accu1, a1);
            const auto a2 = hn::LoadU(d, a + i);
            i += N;
            accu2 = accu_fn(accu2, a2);
            const auto a3 = hn::LoadU(d, a + i);
            i += N;
            accu3 = accu_fn(accu3, a3);
            const auto a4 = hn::LoadU(d, a + i);
            i += N;
            accu4 = accu_fn(accu4, a4);
            const auto a5 = hn::LoadU(d, a + i);
            i += N;
            accu5 = accu_fn(accu5, a5);
            const auto a6 = hn::LoadU(d, a + i);
            i += N;
            accu6 = accu_fn(accu6, a6);
            const auto a7 = hn::LoadU(d, a + i);
            i += N;
            accu7 = accu_fn(accu7, a7);
        }
        constexpr bool is_multiple_of_vec = (Assumptions & UnrollAssumptions::MultipleOfVector) != 0;
        if constexpr (!is_multiple_of_vec) {
            // Boundary case: up to (and including) 7 whole vectors at the end,
            // but not a full (or possibly any) 8th vector.
            for (; (i + N) <= n_elems; i += N) {
                const auto a0 = hn::LoadU(d, a + i);
                accu0 = accu_fn(accu0, a0);
            }
            // Process up any final stragglers of < N elems
            const size_t rem = n_elems - i;
            if (rem != 0) {
                // Lanes OOB will be _zero_
                const auto a0 = hn::LoadN(d, a + i, rem);
                accu1 = accu_fn(accu1, a0);
            }
        } else {
            HWY_DASSERT(i == n_elems);
        }
        // Reduce accumulators in parallel, then reduce down to final:
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

template <int Assumptions = 0>
struct Unroll4X : UnrollBase<Unroll4X<Assumptions>> {
    // Closely inspired by the 4x unrolled dot product implementation in Highway, but uses
    // LoadN for boundary handling instead of LoadU+FirstN. This means `accu_fn` MUST be
    // well-defined when it receives implicitly zeroed entries for OOB elements.
    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    static R reduce_pairwise(
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
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        size_t i = 0;

        for (; (i + 4 * N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            i += N;
            accu0 = accu_fn(accu0, a0, b0);
            const auto a1 = hn::LoadU(d, a + i);
            const auto b1 = hn::LoadU(d, b + i);
            i += N;
            accu1 = accu_fn(accu1, a1, b1);
            const auto a2 = hn::LoadU(d, a + i);
            const auto b2 = hn::LoadU(d, b + i);
            i += N;
            accu2 = accu_fn(accu2, a2, b2);
            const auto a3 = hn::LoadU(d, a + i);
            const auto b3 = hn::LoadU(d, b + i);
            i += N;
            accu3 = accu_fn(accu3, a3, b3);
        }
        constexpr bool is_multiple_of_vec = (Assumptions & UnrollAssumptions::MultipleOfVector) != 0;
        if constexpr (!is_multiple_of_vec) {
            // Boundary case: up to (and including) 3 whole vectors at the end,
            // but not a full (or possibly any) 4th vector.
            for (; (i + N) <= n_elems; i += N) {
                const auto a0 = hn::LoadU(d, a + i);
                const auto b0 = hn::LoadU(d, b + i);
                accu0 = accu_fn(accu0, a0, b0);
            }
            // Process up any final stragglers of < N elems
            const size_t rem = n_elems - i;
            if (rem != 0) {
                // Lanes OOB will be _zero_
                const auto a0 = hn::LoadN(d, a + i, rem);
                const auto b0 = hn::LoadN(d, b + i, rem);
                accu1 = accu_fn(accu1, a0, b0);
            }
        } else {
            HWY_DASSERT(i == n_elems);
        }
        // Reduce accumulators {0, 1} and {2, 3} in parallel, then reduce down to final.
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
    static R reduce(
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
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        size_t i = 0;

        for (; (i + 4 * N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            i += N;
            accu0 = accu_fn(accu0, a0);
            const auto a1 = hn::LoadU(d, a + i);
            i += N;
            accu1 = accu_fn(accu1, a1);
            const auto a2 = hn::LoadU(d, a + i);
            i += N;
            accu2 = accu_fn(accu2, a2);
            const auto a3 = hn::LoadU(d, a + i);
            i += N;
            accu3 = accu_fn(accu3, a3);
        }
        constexpr bool is_multiple_of_vec = (Assumptions & UnrollAssumptions::MultipleOfVector) != 0;
        if constexpr (!is_multiple_of_vec) {
            // Boundary case: up to (and including) 3 whole vectors at the end,
            // but not a full (or possibly any) 4th vector.
            for (; (i + N) <= n_elems; i += N) {
                const auto a0 = hn::LoadU(d, a + i);
                accu0 = accu_fn(accu0, a0);
            }
            // Process up any final stragglers of < N elems
            const size_t rem = n_elems - i;
            if (rem != 0) {
                // Lanes OOB will be _zero_
                const auto a0 = hn::LoadN(d, a + i, rem);
                accu1 = accu_fn(accu1, a0);
            }
        } else {
            HWY_DASSERT(i == n_elems);
        }
        // Reduce accumulators {0, 1} and {2, 3} in parallel, then reduce down to final.
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu0 = accu_reducer_fn(accu0, accu2);
        return lane_reducer_fn(da, accu0);
    }
};

struct Unroll2X : UnrollBase<Unroll2X> {
    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    static R reduce_pairwise(
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
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        size_t i = 0;

        for (; (i + 2 * N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            i += N;
            accu0 = accu_fn(accu0, a0, b0);
            const auto a1 = hn::LoadU(d, a + i);
            const auto b1 = hn::LoadU(d, b + i);
            i += N;
            accu1 = accu_fn(accu1, a1, b1);
        }
        // Boundary case: up to (and including) 1 whole vector at the end
        if ((i + N) <= n_elems) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            i += N;
            accu0 = accu_fn(accu0, a0, b0);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            const auto b0 = hn::LoadN(d, b + i, rem);
            accu1 = accu_fn(accu1, a0, b0);
        }
        accu0 = accu_reducer_fn(accu0, accu1);
        return lane_reducer_fn(da, accu0);
    }

    template <
        typename D,
        typename DA,
        typename T = hn::TFromD<D>,
        typename R = hn::TFromD<DA>,
        typename AccuLoFn,
        typename AccuHiFn,
        typename AccuReducerFn,
        typename LaneReducerFn
    >
    static R reduce_pairwise_with_split_accu(
            const D d,
            const DA da,
            const T* HWY_RESTRICT a, const T* HWY_RESTRICT b,
            size_t n_elems,
            const hn::Vec<DA> init_accu,
            const AccuLoFn accu_lo_fn,
            const AccuHiFn accu_hi_fn,
            const AccuReducerFn accu_reducer_fn,
            const LaneReducerFn lane_reducer_fn) noexcept
    {
        using AccuV = hn::Vec<DA>;
        const size_t N = hn::Lanes(d);
        // Although this is 2x unrolled for (pairwise) loads, it's technically 4x unrolled for accumulators.
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        AccuV accu2 = init_accu;
        AccuV accu3 = init_accu;
        size_t i = 0;

        for (; (i + 2*N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            i += N;
            accu0 = accu_lo_fn(accu0, a0, b0);
            accu1 = accu_hi_fn(accu1, a0, b0);
            const auto a1 = hn::LoadU(d, a + i);
            const auto b1 = hn::LoadU(d, b + i);
            i += N;
            accu2 = accu_lo_fn(accu2, a1, b1);
            accu3 = accu_hi_fn(accu3, a1, b1);
        }
        // Boundary case: up to (and including) 1 whole vector at the end
        if ((i + N) <= n_elems) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            i += N;
            accu0 = accu_lo_fn(accu0, a0, b0);
            accu1 = accu_hi_fn(accu1, a0, b0);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            const auto b0 = hn::LoadN(d, b + i, rem);
            accu0 = accu_lo_fn(accu0, a0, b0);
            accu1 = accu_hi_fn(accu1, a0, b0);
        }
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
    static R reduce(
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
        AccuV accu0 = init_accu;
        AccuV accu1 = init_accu;
        size_t i = 0;

        for (; (i + 2 * N) <= n_elems;) {
            const auto a0 = hn::LoadU(d, a + i);
            i += N;
            accu0 = accu_fn(accu0, a0);
            const auto a1 = hn::LoadU(d, a + i);
            i += N;
            accu1 = accu_fn(accu1, a1);
        }
        // Boundary case: up to (and including) 1 whole vector at the end
        if ((i + N) <= n_elems) {
            const auto a0 = hn::LoadU(d, a + i);
            i += N;
            accu0 = accu_fn(accu0, a0);
        }
        // Process up any final stragglers of < N elems
        const size_t rem = n_elems - i;
        if (rem != 0) {
            // Lanes OOB will be _zero_
            const auto a0 = hn::LoadN(d, a + i, rem);
            accu1 = accu_fn(accu1, a0);
        }
        accu0 = accu_reducer_fn(accu0, accu1);
        return lane_reducer_fn(da, accu0);
    }
};

template <typename T> requires (hwy::IsFloat<T>())
HWY_NOINLINE double
my_hwy_square_euclidean_distance_unrolled_impl(const T* a, const T* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<T> d;
    const auto accu_fn = [](auto accu, auto lhs, auto rhs) noexcept {
        const auto sub = hn::Sub(lhs, rhs);
        return hn::MulAdd(sub, sub, accu); // note: using fused multiply-add
    };
    return Unroll8X<>::reduce_pairwise_with_sum(d, d, a, b, sz, accu_fn);
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
    // TODO split into single accu fn with 4 accu ref params?
    const auto accu_lo_fn = [df](auto accu, auto lhs, auto rhs) noexcept {
        const auto sub = hn::Sub(hn::PromoteLowerTo(df, lhs), hn::PromoteLowerTo(df, rhs));
        return hn::MulAdd(sub, sub, accu);
    };
    const auto accu_hi_fn = [df](auto accu, auto lhs, auto rhs) noexcept {
        const auto sub = hn::Sub(hn::PromoteUpperTo(df, lhs), hn::PromoteUpperTo(df, rhs));
        return hn::MulAdd(sub, sub, accu);
    };
    return Unroll2X::reduce_pairwise_with_split_accu(dbf16, df, a_bf16, b_bf16, sz, hn::Zero(df),
                                                     accu_lo_fn, accu_hi_fn, VecAdd(), LaneReduceSum());
}

// Important: `sz` should be low enough that the intermediate i32 sum does not overflow!
// TODO __attribute__((noclone)) to avoid GCC cloning out a massive blob of AVX-512 code to
//  process constant 256 bytes in one go? Needs to be benchmarked first...
HWY_NOINLINE int32_t
sub_mul_add_i8s_via_i16_to_i32(const int8_t* a, const int8_t* b, size_t sz) {
    const hn::ScalableTag<int8_t>                  d8;
    const hn::Repartition<int16_t, decltype(d8)>  d16;
    const hn::Repartition<int32_t, decltype(d16)> d32;

    using SumV = decltype(hn::Zero(d32));
    const size_t N = hn::Lanes(d8);
    size_t i = 0;
    SumV sum0 = hn::Zero(d32);
    SumV sum1 = hn::Zero(d32);
    SumV sum2 = hn::Zero(d32);
    SumV sum3 = hn::Zero(d32);

    // TODO factor out multi-accumulator functions so that we don't need the explicit scaffolding
    auto do_compute_and_accumulate = [d16, d32](auto lhs, auto rhs, auto& acc0, auto& acc1, auto& acc2, auto& acc3) noexcept {
        const auto sub_l_i16 = hn::Sub(hn::PromoteLowerTo(d16, lhs), hn::PromoteLowerTo(d16, rhs));
        const auto sub_u_i16 = hn::Sub(hn::PromoteUpperTo(d16, lhs), hn::PromoteUpperTo(d16, rhs));
        acc0 = hn::ReorderWidenMulAccumulate(d32, sub_l_i16, sub_l_i16, acc0, acc1);
        acc2 = hn::ReorderWidenMulAccumulate(d32, sub_u_i16, sub_u_i16, acc2, acc3);
    };

    for (; (i + 2*N) <= sz;) {
        const auto a0 = hn::LoadU(d8, a + i);
        const auto b0 = hn::LoadU(d8, b + i);
        i += N;
        do_compute_and_accumulate(a0, b0, sum0, sum1, sum2, sum3);
        const auto a1 = hn::LoadU(d8, a + i);
        const auto b1 = hn::LoadU(d8, b + i);
        i += N;
        do_compute_and_accumulate(a1, b1, sum0, sum1, sum2, sum3);
    }
    // Boundary case: < 2 whole vectors at the end
    for (; (i + N) <= sz; i += N) {
        const auto a0 = hn::LoadU(d8, a + i);
        const auto b0 = hn::LoadU(d8, b + i);
        do_compute_and_accumulate(a0, b0, sum0, sum1, sum2, sum3);
    }
    // Process up any final stragglers of < N elems
    const size_t rem = sz - i;
    if (rem != 0) [[unlikely]] {
        // Lanes OOB will be zero, i.e. they will not contribute to distance.
        const auto a0 = hn::LoadN(d8, a + i, rem);
        const auto b0 = hn::LoadN(d8, b + i, rem);
        do_compute_and_accumulate(a0, b0, sum0, sum1, sum2, sum3);
    }
    sum0 = hn::Add(sum0, sum1);
    sum2 = hn::Add(sum2, sum3);
    sum0 = hn::Add(sum0, sum2);
    return hn::ReduceSum(d32, sum0);
}

HWY_NOINLINE double
my_hwy_square_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) VESPA_HWY_NOEXCEPT {
    constexpr size_t LOOP_COUNT = 256;
    double sum = 0;
    size_t i = 0;
    for (; i + LOOP_COUNT <= sz; i += LOOP_COUNT) {
        sum += sub_mul_add_i8s_via_i16_to_i32(a + i, b + i, LOOP_COUNT);
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
    const hn::ScalableTag<uint64_t> d;
    const auto accu_fn = [](auto accu, auto v) noexcept {
        return hn::Add(hn::PopulationCount(v), accu);
    };
    return Unroll8X<>::reduce_with_sum(d, d, a, sz, accu_fn);
}

// Perhaps paradoxically, using `noinline` here can result in better codegen since the compiler
// can clone out a distinct function specialization that is completely unrolled for a particular
// iteration count (e.g. 256).
template <int Assumptions, typename D8>
HWY_NOINLINE int32_t
mul_add_i8_as_i32(D8 d8, const int8_t* a, const int8_t* b, size_t sz) noexcept {
    const hn::ScalableTag<int32_t> d32;
    const auto accu_fn = [d32](auto accu, auto lhs_i8, auto rhs_i8) noexcept {
        // FIXME Highway does _not_ generate an SDOT instruction on NEON+dotproduct for i8->i32,
        //  only for NEON+BF16 (since there is no distinct target for just dotproduct), which means
        //  this is ~4x slower than it could ideally be on a MacBook Pro M1...!
        return hn::SumOfMulQuadAccumulate(d32, lhs_i8, rhs_i8, accu);
    };
    return Unroll4X<Assumptions>::reduce_pairwise_with_sum(d8, d32, a, b, sz, accu_fn);
}

HWY_NOINLINE int64_t
my_hwy_i8_dot_product(const int8_t* a, const int8_t* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<int8_t> d8;
    static_assert(hn::MaxLanes(d8) <= 256);
    constexpr size_t LOOP_COUNT = 256;
    int64_t sum = 0;
    size_t i = 0;
    for (; i + LOOP_COUNT <= sz; i += LOOP_COUNT) {
        sum += mul_add_i8_as_i32<UnrollAssumptions::MultipleOfVector>(d8, a + i, b + i, LOOP_COUNT);
    }
    if (sz > i) [[unlikely]] {
        sum += mul_add_i8_as_i32<0>(d8, a + i, b + i, sz - i);
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
