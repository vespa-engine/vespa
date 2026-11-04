// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Avoid include guard linter warnings
#ifndef VESPA_HWY_KERNEL_DISPATCH_INL_H_CALM_DOWN_LINTER
#define VESPA_HWY_KERNEL_DISPATCH_INL_H_CALM_DOWN_LINTER
#endif

#include "invocation_meta.h"

#include <hwy/base.h>

// Per-target include guard. See https://google.github.io/highway/en/master/faq.html Q5.4.
#if defined(VESPA_HWY_KERNEL_DISPATCH_INL_H_TARGET) == defined(HWY_TARGET_TOGGLE)
#ifdef VESPA_HWY_KERNEL_DISPATCH_INL_H_TARGET
#undef VESPA_HWY_KERNEL_DISPATCH_INL_H_TARGET
#else
#define VESPA_HWY_KERNEL_DISPATCH_INL_H_TARGET
#endif

#include <hwy/highway.h>

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;

// SFINAE overload matcher that selects functions for use when the total number
// of available kernel accumulators is TotalCount, and the kernel function itself
// uses FnArity of these _per invocation_.
template <size_t TotalCount, size_t FnArity>
struct AccusAndFnArity {};

// We (partially) decouple accumulator parallelism, the unrolling factor and
// how many accumulators a given kernel function uses. "Partially" is because
// we inherently can't use a kernel function requiring _more_ accumulators than
// there are accumulators present, and if we use an 1-ary kernel function with
// an unroll factor of 2 and 4 parallel accumulators, the unrolled loop won't
// be able to use all available accumulators (can only use 2).
//
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
//
// Due to the "manual" scheduling work needed, we don't support arbitrary total
// accumulator counts or kernel arity for reduction kernels:
//  - total accumulator count can be 4 or 8
//  - kernel arity can be 1, 2, 4 or 8
//
// Note that for all dispatch functions, `rhs` might be empty (i.e. an unary
// kernel that takes 0-n accumulators).
//
// The kernel/dispatch return type may be `void`.

// 0-way (used for non-reduction kernels):

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<0, 0>, KernelFn&& kernel_fn, LhsT lhs,
                         RhsT&&... rhs) noexcept {
    return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)...);
}

// 4-way:

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<4, 1>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, RhsT&&... rhs) noexcept {
    constexpr size_t my_idx = meta.unroll_index() % 4;
    if constexpr (my_idx == 0) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0);
    } else if (my_idx == 1) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu1);
    } else if (my_idx == 2) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu2);
    } else if (my_idx == 3) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu3);
    }
}

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<4, 2>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, RhsT&&... rhs) noexcept {
    constexpr size_t my_idx = meta.unroll_index() % 2;
    if constexpr (my_idx == 0) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0, accu1);
    } else if (my_idx == 1) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu2, accu3);
    }
}

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<4, 4>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, RhsT&&... rhs) noexcept {
    return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0, accu1, accu2, accu3);
}

// 8-way:

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<8, 1>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, AccuV& accu4, AccuV& accu5, AccuV& accu6,
                         AccuV& accu7, RhsT&&... rhs) noexcept {
    constexpr size_t my_idx = meta.unroll_index() % 8;
    if constexpr (my_idx == 0) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0);
    } else if (my_idx == 1) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu1);
    } else if (my_idx == 2) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu2);
    } else if (my_idx == 3) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu3);
    } else if (my_idx == 4) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu4);
    } else if (my_idx == 5) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu5);
    } else if (my_idx == 6) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu6);
    } else if (my_idx == 7) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu7);
    }
}

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<8, 2>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, AccuV& accu4, AccuV& accu5, AccuV& accu6,
                         AccuV& accu7, RhsT&&... rhs) noexcept {
    constexpr size_t my_idx = meta.unroll_index() % 4;
    if constexpr (my_idx == 0) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0, accu1);
    } else if (my_idx == 1) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu2, accu3);
    } else if (my_idx == 2) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu4, accu5);
    } else if (my_idx == 3) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu6, accu7);
    }
}

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<8, 4>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, AccuV& accu4, AccuV& accu5, AccuV& accu6,
                         AccuV& accu7, RhsT&&... rhs) noexcept {
    constexpr size_t my_idx = meta.unroll_index() % 2;
    if constexpr (my_idx == 0) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0, accu1, accu2, accu3);
    } else if (my_idx == 1) {
        return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu4, accu5, accu6, accu7);
    }
}

template <InvocationMeta MetaT, typename KernelFn, typename LhsT, typename AccuV, typename... RhsT>
HWY_API auto my_dispatch(const MetaT meta, AccusAndFnArity<8, 8>, KernelFn&& kernel_fn, LhsT lhs, AccuV& accu0,
                         AccuV& accu1, AccuV& accu2, AccuV& accu3, AccuV& accu4, AccuV& accu5, AccuV& accu6,
                         AccuV& accu7, RhsT&&... rhs) noexcept {
    return kernel_fn(meta, lhs, std::forward<RhsT>(rhs)..., accu0, accu1, accu2, accu3, accu4, accu5, accu6, accu7);
}

} // namespace HWY_NAMESPACE
} // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#endif // VESPA_HWY_KERNEL_DISPATCH_INL_H_TARGET
