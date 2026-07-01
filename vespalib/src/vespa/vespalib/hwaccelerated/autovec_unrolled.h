// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace vespalib::hwaccelerated {

// Invokes `fn(i)` once for every value `i` in [0, sz) and adds each result to one
// of N partial sums. The sum of partial sums is then returned as the final value.
// For floating point sums, this is explicitly and intentionally _not_ guaranteed
// to yield the exact same result as if the output of `fn` had been sequentially
// summed to a single accumulator. And that's why the compiler dares not optimize
// loops in such a way in general (modulo `-ffast-math`, which is yolo-mode).
template <size_t N, typename SumT, typename Fn>
[[nodiscard]] SumT sum_indexed_unrolled(const size_t sz, Fn fn) noexcept(noexcept(fn(0))) {
    SumT   partial[N] = {};
    size_t i = 0;
    for (; (i + N) <= sz; i += N) {
        for (size_t j = 0; j < N; ++j) {
            partial[j] += fn(i + j);
        }
    }
    SumT sum{};
    for (; i < sz; ++i) {
        sum += fn(i);
    }
    // A "proper" vectorized version would use an N-way reduction tree, but this will do.
    for (size_t j = 0; j < N; ++j) {
        sum += partial[j];
    }
    return sum;
}

} // namespace vespalib::hwaccelerated
