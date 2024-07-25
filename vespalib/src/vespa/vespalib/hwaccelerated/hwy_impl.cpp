// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hwy_impl.h"
#include <hwy/base.h>
#include <cassert>

// 0 => static dispatch (single target)
// 1 => dynamic dispatch (multiple targets)
#define VESPA_HWY_DYNAMIC 0

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

template <typename T> requires (hwy::IsFloat<T>())
HWY_INLINE T my_hwy_dot_impl(const T* HWY_RESTRICT a, const T* HWY_RESTRICT b, size_t sz) noexcept {
    const hn::ScalableTag<T> d;
    return hwy::ConvertScalarTo<T>(hn::Dot::Compute<0>(d, a, b, sz));
}

HWY_NOINLINE float my_hwy_dot_float(const float* HWY_RESTRICT a, const float* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    return my_hwy_dot_impl(a, b, sz);
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
        const auto accu_reducer_fn = [](auto lhs, auto rhs) noexcept {
            return hn::Add(lhs, rhs);
        };
        const auto lane_reducer_fn = [](auto d0, auto accu) noexcept {
            return hn::ReduceSum(d0, accu);
        };
        return Derived::reduce_pairwise(d, da, a, b, n_elems, hn::Zero(da), accu_fn, accu_reducer_fn, lane_reducer_fn);
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
        const auto accu_reducer_fn = [](auto lhs, auto rhs) noexcept {
            return hn::Add(lhs, rhs);
        };
        const auto lane_reducer_fn = [](auto d0, auto accu) noexcept {
            return hn::ReduceSum(d0, accu);
        };
        return Derived::reduce(d, da, a, n_elems, hn::Zero(da), accu_fn, accu_reducer_fn, lane_reducer_fn);
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
            assert(i == n_elems);
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
            assert(i == n_elems);
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
            assert(i == n_elems);
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
            assert(i == n_elems);
        }
        // Reduce accumulators {0, 1} and {2, 3} in parallel, then reduce down to final.
        accu0 = accu_reducer_fn(accu0, accu1);
        accu2 = accu_reducer_fn(accu2, accu3);
        accu0 = accu_reducer_fn(accu0, accu2);
        return lane_reducer_fn(da, accu0);
    }
};

// Non-unrolled version
template <typename D, typename T = hn::TFromD<D>> requires (hwy::IsFloat<T>())
HWY_INLINE double
my_hwy_square_euclidean_distance_impl(D d, const T* HWY_RESTRICT a, const T* HWY_RESTRICT b, size_t sz) VESPA_HWY_NOEXCEPT {
    const size_t N = hn::Lanes(d);
    size_t i = 0;
    auto accu = hn::Zero(d);
    if (sz >= N) {
        for (; i <= sz - N; i += N) {
            const auto a0 = hn::LoadU(d, a + i);
            const auto b0 = hn::LoadU(d, b + i);
            const auto sub0 = hn::Sub(a0, b0);
            accu = hn::MulAdd(sub0, sub0, accu);
        }
    }
    // TODO early out for aligned vec sizes
    const auto rem = sz - i;
    if (rem != 0) {
        // Lanes OOB will be zero
        const auto a0 = hn::LoadN(d, a + i, rem);
        const auto b0 = hn::LoadN(d, b + i, rem);
        const auto sub0 = hn::Sub(a0, b0);
        accu = hn::MulAdd(sub0, sub0, accu);
    }
    return hn::ReduceSum(d, accu);
}

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

HWY_NOINLINE double
my_hwy_square_euclidean_distance_float(const float* a, const float* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<float> d;
    return my_hwy_square_euclidean_distance_impl(d, a, b, sz);
}

HWY_NOINLINE double
my_hwy_square_euclidean_distance_double(const double* a, const double* b, size_t sz) VESPA_HWY_NOEXCEPT {
    const hn::ScalableTag<double> d;
    return my_hwy_square_euclidean_distance_impl(d, a, b, sz);
}

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

// Important: `sz` should be low enough that the intermediate i32 sum does not overflow!
// TODO __attribute__((noclone)) to avoid GCC cloning out a massive blob of AVX-512 code to
//  process constant 256 bytes in one go? Needs to be benchmarked first...
HWY_NOINLINE int32_t
sub_mul_add_i8s_via_i16_to_i32(const int8_t* a, const int8_t* b, size_t sz) {
    const hn::ScalableTag<int8_t>  d8;
    const hn::ScalableTag<int16_t> d16;
    const hn::ScalableTag<int32_t> d32;

    using SumV = decltype(hn::Zero(d32));
    const size_t N = hn::Lanes(d8);
    size_t i = 0;
    SumV sum0 = hn::Zero(d32);
    SumV sum1 = hn::Zero(d32);
    SumV sum2 = hn::Zero(d32);
    SumV sum3 = hn::Zero(d32);

    if (sz >= N) [[likely]] {
        for (; i <= sz - N; i += N) {
            const auto lhs = hn::LoadU(d8, a + i);
            const auto rhs = hn::LoadU(d8, b + i);
            // This is very similar to the `Unroll4x::reduce_pairwise()` main loop, except we use 4-way parallel
            // summing based on a _single_ pairwise vector load, rather than having 4 loads in flight.
            // This is because we use a lot of vector operations/registers to promote i8 -> i16 for subs,
            // then i16 -> i32 for multiply-adds.
            const auto sub_l_i16 = hn::Sub(hn::PromoteLowerTo(d16, lhs), hn::PromoteLowerTo(d16, rhs));
            const auto sub_u_i16 = hn::Sub(hn::PromoteUpperTo(d16, lhs), hn::PromoteUpperTo(d16, rhs));

            const auto l_l_i32 = hn::PromoteLowerTo(d32, sub_l_i16);
            sum0 = hn::MulAdd(l_l_i32, l_l_i32, sum0);
            const auto l_u_i32 = hn::PromoteUpperTo(d32, sub_l_i16);
            sum1 = hn::MulAdd(l_u_i32, l_u_i32, sum1);
            const auto u_l_i32 = hn::PromoteLowerTo(d32, sub_u_i16);
            sum2 = hn::MulAdd(u_l_i32, u_l_i32, sum2);
            const auto u_u_i32 = hn::PromoteUpperTo(d32, sub_u_i16);
            sum3 = hn::MulAdd(u_u_i32, u_u_i32, sum3);
        }
    }
    const size_t rem = sz - i;
    if (rem != 0) [[unlikely]] {
        // Lanes OOB will be zero, i.e. they will not contribute to distance.
        const auto lhs = hn::LoadN(d8, a + i, rem);
        const auto rhs = hn::LoadN(d8, b + i, rem);
        // Code duplication between loops is unfortunate but intentional; using a lambda with
        // reference-capture of sum{0-3} triggers suboptimal codegen with spilling to memory
        // of all temporary sum registers after the completion of the main loop, at least for
        // AVX-512. Duplication lets the compiler be clever enough to reuse registers across
        // loops and final summation.
        const auto sub_l_i16 = hn::Sub(hn::PromoteLowerTo(d16, lhs), hn::PromoteLowerTo(d16, rhs));
        const auto sub_u_i16 = hn::Sub(hn::PromoteUpperTo(d16, lhs), hn::PromoteUpperTo(d16, rhs));

        const auto l_l_i32 = hn::PromoteLowerTo(d32, sub_l_i16);
        sum0 = hn::MulAdd(l_l_i32, l_l_i32, sum0);
        const auto l_u_i32 = hn::PromoteUpperTo(d32, sub_l_i16);
        sum1 = hn::MulAdd(l_u_i32, l_u_i32, sum1);
        const auto u_l_i32 = hn::PromoteLowerTo(d32, sub_u_i16);
        sum2 = hn::MulAdd(u_l_i32, u_l_i32, sum2);
        const auto u_u_i32 = hn::PromoteUpperTo(d32, sub_u_i16);
        sum3 = hn::MulAdd(u_u_i32, u_u_i32, sum3);
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

template <int Assumptions, typename D8>
int32_t
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
HWY_EXPORT(my_hwy_dot_double);
HWY_EXPORT(my_hwy_popcount);
HWY_EXPORT(my_hwy_square_euclidean_distance_float_unrolled);
HWY_EXPORT(my_hwy_square_euclidean_distance_double_unrolled);
HWY_EXPORT(my_hwy_square_euclidean_distance_i8);
HWY_EXPORT(my_hwy_i8_dot_product);

float HwyAccelerator::dotProduct(const float* a, const float* b, size_t sz) const noexcept {
    return HWY_DYNAMIC_DISPATCH(my_hwy_dot_float)(a, b, sz);
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

#else // if VESPA_HWY_DYNAMIC

// TODO figure out why Highway dot product is faster for shorter vectors (1000), but
//  seemingly _slower_ for longer vectors (4000)... õ_o
float HwyAccelerator::dotProduct(const float* a, const float* b, size_t sz) const noexcept {
    return my_hwy_dot_float(a, b, sz);
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
    // return my_hwy_square_euclidean_distance_float(a, b, sz);
    return my_hwy_square_euclidean_distance_float_unrolled(a, b, sz);
}

double HwyAccelerator::squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept {
    //return my_hwy_square_euclidean_distance_double(a, b, sz);
    return my_hwy_square_euclidean_distance_double_unrolled(a, b, sz);
}

#endif // VESPA_HWY_DYNAMIC

} // namespace vespalib::hwaccelerated

#endif // HWY_ONCE
