// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <vector>

namespace vespalib::eval {

// This file contains implementations for the generic nested loops
// used by DenseJoinPlan, DenseReducePlan and similar. The loops act
// like arbitrarily nested for-loops that are index-based where each
// loop-level has a different stride that modifies the overall
// index. An initial index is passed to the top-level function, which
// is then modified by each loop-layer and finally passed back to a
// callable for each iteration of the inner loop. There are different
// implementations for traversing different numbers of index spaces in
// parallel. Note that all loop layers must have at least 1 iteration.

namespace nested_loop {

//-----------------------------------------------------------------------------

template <typename F, size_t N> void execute_few(size_t idx, const size_t *loop, const size_t *stride, const F &f) {
    if constexpr (N == 0) {
        f(idx);
    } else {
        for (size_t i = 0; i < *loop; ++i, idx += *stride) {
            execute_few<F, N - 1>(idx, loop + 1, stride + 1, f);
        }
    }
}

template <typename F> void execute_many(size_t idx, const size_t *loop, const size_t *stride, size_t levels, const F &f) {
    for (size_t i = 0; i < *loop; ++i, idx += *stride) {
        if ((levels - 1) == 3) {
            execute_few<F, 3>(idx, loop + 1, stride + 1, f);
        } else {
            execute_many<F>(idx, loop + 1, stride + 1, levels - 1, f);
        }
    }
}

//-----------------------------------------------------------------------------

template <typename F, size_t N> void execute_few(size_t idx1, size_t idx2, const size_t *loop, const size_t *stride1, const size_t *stride2, const F &f) {
    if constexpr (N == 0) {
        f(idx1, idx2);
    } else {
        for (size_t i = 0; i < *loop; ++i, idx1 += *stride1, idx2 += *stride2) {
            execute_few<F, N - 1>(idx1, idx2, loop + 1, stride1 + 1, stride2 + 1, f);
        }
    }
}

template <typename F> void execute_many(size_t idx1, size_t idx2, const size_t *loop, const size_t *stride1, const size_t *stride2, size_t levels, const F &f) {
    for (size_t i = 0; i < *loop; ++i, idx1 += *stride1, idx2 += *stride2) {
        if ((levels - 1) == 3) {
            execute_few<F, 3>(idx1, idx2, loop + 1, stride1 + 1, stride2 + 1, f);
        } else {
            execute_many<F>(idx1, idx2, loop + 1, stride1 + 1, stride2 + 1, levels - 1, f);
        }
    }
}

//-----------------------------------------------------------------------------

} // implementation details

// Run a nested loop and pass indexes to 'f'
template <typename F, typename V>
void run_nested_loop(size_t idx, const V &loop, const V &stride, const F &f) {
    size_t levels = loop.size();
    switch(levels) {
    case 0: return f(idx);
    case 1: return nested_loop::execute_few<F, 1>(idx, &loop[0], &stride[0], f);
    case 2: return nested_loop::execute_few<F, 2>(idx, &loop[0], &stride[0], f);
    case 3: return nested_loop::execute_few<F, 3>(idx, &loop[0], &stride[0], f);
    default: return nested_loop::execute_many<F>(idx, &loop[0], &stride[0], levels, f);
    }
}

// Run two nested loops in parallel and pass both indexes to 'f'. Note
// that 'loop' is shared, which means that only individual strides may
// differ between the two loops.
template <typename F, typename V>
void run_nested_loop(size_t idx1, size_t idx2, const V &loop, const V &stride1, const V &stride2, const F &f) {
    size_t levels = loop.size();
    switch(levels) {
    case 0: return f(idx1, idx2);
    case 1: return nested_loop::execute_few<F, 1>(idx1, idx2, &loop[0], &stride1[0], &stride2[0], f);
    case 2: return nested_loop::execute_few<F, 2>(idx1, idx2, &loop[0], &stride1[0], &stride2[0], f);
    case 3: return nested_loop::execute_few<F, 3>(idx1, idx2, &loop[0], &stride1[0], &stride2[0], f);
    default: return nested_loop::execute_many<F>(idx1, idx2, &loop[0], &stride1[0], &stride2[0], levels, f);
    }
}

} // namespace
