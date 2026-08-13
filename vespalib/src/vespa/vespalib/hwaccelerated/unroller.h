// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <iostream>
#include <utility>

namespace vespalib::hwaccelerated {

// Typed unrolling index wrapper to allow callee to easily do constexpr stuff on it
template <size_t Idx> struct UnrollIdx {
    static constexpr size_t value = Idx;
};

/**
 * Unroller utility which lets a functor invocation be explicitly N-way duplicated in
 * a sequence. All called functors will receive (and must therefore accept) as their
 * first argument an `UnrollIdx<I>` value, where `I` is the unrolling "index" of that
 * invocation, starting at 0.
 *
 * E.g.
 *
 *   auto foo = [](auto idx) {};
 *   Unroller::unroll<4>(foo);
 *
 * will result in the following sequence of invocations:
 *
 *   foo(UnrollIdx<0>{})
 *   foo(UnrollIdx<1>{})
 *   foo(UnrollIdx<2>{})
 *   foo(UnrollIdx<3>{})
 *
 * Since the `UnrollIdx` is a fully constexpr value, it can be used for clever things
 * such as striping usage of accumulator registers based on the index value etc. This
 * differs from regular compiler-unrolled loops (which should be preferred in most
 * situations), where the value of loop induction variables is not something you can
 * _explicitly_ use for meta-programming.
 *
 * Additional functor arguments can be passed to unroll(); these will be passed to
 * each invocation _after_ the first `UnrollIdx<I>` argument.
 *
 * Function arguments are std::forward()'ed to each unrolled invocation of the functor
 * to allow for reference type arguments, so be careful to not pass (and consume) any
 * _rvalue_ references.
 */
class Unroller {
    // We make the simplifying, yet hopefully reasonable, assumption that noexcept-ness
    // does not depend on the unroll index passed to the unrolled function.
    // All unrolling indirections are force-inlined and should therefore never result in
    // any recursion. We do not use the `flatten` attribute, as that decision should be
    // left up to the callee.
    template <size_t UnrollLbound, size_t UnrollUbound, typename Fn, typename... FnArgs>
    static inline __attribute__((always_inline)) void
    do_unroll(Fn&& fn, FnArgs&&... fn_args) noexcept(noexcept(fn(UnrollIdx<0>{}, std::forward<FnArgs>(fn_args)...))) {
        constexpr size_t unroll_idx = UnrollLbound;
        fn(UnrollIdx<unroll_idx>{}, std::forward<FnArgs>(fn_args)...);
        if constexpr (UnrollLbound + 1 < UnrollUbound) {
            do_unroll<UnrollLbound + 1, UnrollUbound>(std::forward<Fn>(fn), std::forward<FnArgs>(fn_args)...);
        }
    }

public:
    template <size_t UnrollCount, typename Fn, typename... FnArgs>
    static inline __attribute__((always_inline)) void
    unroll(Fn&& fn, FnArgs&&... fn_args) noexcept(noexcept(fn(UnrollIdx<0>{}, std::forward<FnArgs>(fn_args)...))) {
        static_assert(UnrollCount > 0, "must have at least one invocation");
        do_unroll<0, UnrollCount>(std::forward<Fn>(fn), std::forward<FnArgs>(fn_args)...);
    }
};

} // namespace vespalib::hwaccelerated
