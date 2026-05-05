// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <bit>
#include <cassert>
#include <cmath>
#include <concepts>
#include <span>

namespace vespalib::quant {

// The quotes and examples here are adapted from
// "The Walsh Hadamard Transform - Basics and Applications" (2021)
// by Sean O'Connor (in public domain).

// Normalize a Walsh-Hadamard-transformed vector so that its magnitude is
// the same as prior to the transformation.
template <std::floating_point T>
void post_hadamard_normalize(T* v, const size_t n) {
    // Normalization is just dividing all elements by the square root of the size
    const T sqrt_recip = T{1} / std::sqrt(n);
    for (size_t i = 0; i < n; ++i) {
        v[i] *= sqrt_recip;
    }
}

/**
 * Fast _out-of-place_ Walsh-Hadamard Transform (not normalized).
 *
 * Preconditions:
 *   - the input array and the temporary array must not alias.
 *   - the input size must be a power of two
 *   - the temporary array must be at least as large as the input array
 *
 * Note that this function alternates between writing to _both_ arrays, and
 * returns a pointer to the array that actually ended up receiving the final
 * result. This is either the input or the temporary array.
 *
 * The temporary array does not have to be value-initialized prior to calling
 * this function.
 */
template <typename T>
[[nodiscard]] T* hadamard(T* v, T* tmp, const size_t n) noexcept {
    if (n == 0) [[unlikely]] {
        return v;
    }
    const size_t stages = std::bit_width(n) - 1;
    assert(std::has_single_bit(n));
    /*
     * "Given an array of data that is some integer power of 2 in length, you
     *  can step through the array data pairwise. The sum of each pair of array
     *  elements is placed sequentially in the lower half of the temporary
     *  array (the same size as the data array), and the difference of the pair
     *  of elements is placed sequentially in the upper half of the temporary
     *  array. Alike terms end up being grouped sequentially in the temporary
     *  array. You then swap the data array and temporary array and repeat the
     *  process. Stopping when there are no more alike terms to add and
     *  subtract, that is to say when all the output terms are unique patterns
     *  of addition and subtraction. That happens after log2(n) stages, where
     *  n is the length of the original data array."
     *
     *  Example of 4-point out-of-place WHT:
     *          2-point      4-point
     * | a |    | a+b |    | a+b+c+d |
     * | b | -> | c+d | -> | a-b+c-d |
     * | c |    | a-b |    | a+b-c-d |
     * | d |    | c-d |    | a-b-c+d |
     */
    T* in = v;
    T* out = tmp;
    for (size_t i = 0; i < stages; ++i) {
        // Each stage processes elements pairwise, shoveling additions into the lower
        // half (lo + ...) and subtractions into the top half (hi + ...) sequentially.
        for (size_t j = 0, lo = 0, hi = n/2; j < n; j += 2, ++lo, ++hi) {
            T a = in[j];
            T b = in[j + 1];
            out[lo] = a + b;
            out[hi] = a - b;
        }
        std::swap(in, out);
    }
    return in; // since we swapped in/out before exiting the loop the last time
}

/**
 * Fast _out-of-place_ Walsh-Hadamard Transform, with post-normalization.
 *
 * See `hadamard(v, tmp, n)` for pre/post-conditions.
 */
template <typename T>
[[nodiscard]] T* hadamard_normalized(T* v, T* tmp, const size_t n) noexcept {
    T* res = hadamard(v, tmp, n);
    post_hadamard_normalize(res, n);
    return res;
}

/**
 * Fast _in-place_ Walsh-Hadamard Transform (not normalized).
 *
 * Precondition: input vector must have a power of 2 length.
 */
template <typename T>
void hadamard(T* v, const size_t n) noexcept {
    /*
     * "The in-place algorithm requires you to keep track of where alike terms
     *  are. Noting that alike terms that have been added and subtracted and
     *  put back in place are no longer alike. The indexing requirements then
     *  involve pairwise blocks of data. The block size increasing at each stage
     *  of the calculation."
     *
     * Example of 8-point in-place WHT:
     * "Alike terms have the same number in brackets. In the first stage the
     *  block size is 1, the second stage 2, the third stage 4. You go through
     *  blocks pairwise, adding and subtracting alike terms".
     *
     *             2-point       4-point              8-point
     * |a (1)|    |a+b (1)|    |a+b+c+d (1)|    |a+b+c+d+e+f+g+h (1)|
     * |b (1)|    |a-b (2)|    |a-b+c-d (2)|    |a-b+c-d+e-f+g-h (2)|
     * |c (1)|    |c+d (1)|    |a+b-c-d (3)|    |a+b-c-d+e+f-g-h (3)|
     * |d (1)| -> |c-d (2)| -> |a-b-c+d (4)| -> |a-b-c+d+e-f-g+h (4)|
     * |e (1)|    |e+f (1)|    |e+f+g+h (1)|    |a+b+c+d-e-f-g-h (5)|
     * |f (1)|    |e-f (2)|    |e-f+g-h (2)|    |a-b+c-d-e+f-g+h (6)|
     * |g (1)|    |g+h (1)|    |e+f-g-h (3)|    |a+b-c-d-e-f+g+h (7)|
     * |h (1)|    |g-h (2)|    |e-f-g+h (4)|    |a-b-c+d-e+f+g-h (8)|
     */
    assert(n == 0 || std::has_single_bit(n));
    // Gap to next alike term (block size); doubles per iteration
    for (size_t h = 1; h < n; h += h) {
        size_t i = 0;
        while (i < n) {
            const size_t j = i + h; // Current block extent
            while (i < j) {
                T a = v[i];
                T b = v[i + h]; // Alike term in _next_ block
                v[i]     = a + b;
                v[i + h] = a - b;
                ++i;
            }
            // We process blocks in pairs, so skip past the second block that
            // we've already wrangled in the above loop.
            i += h;
        }
    }
}

/**
 * Fast _in-place_ Walsh-Hadamard Transform, with post-normalization.
 *
 * See `hadamard(v, n)` for pre/post-conditions.
 */
template <typename T>
void hadamard_normalized(T* v, const size_t n) noexcept {
    hadamard(v, n);
    post_hadamard_normalize(v, n);
}

} // namespace vespalib::quant
