// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cmath>
#include <cstddef>
#include <limits>
#include <span>

namespace vespalib::quant {

/*
 * Finds the centroid whose value has the smallest absolute difference from `v`
 * and returns its index in the `centroids` array. If `v` is at the exact boundary
 * between two centroids (i.e. it's the same distance from both), the _first_
 * centroid index will be returned.
 *
 * Preconditions:
 *  - centroids.size() > 0
 *  - `v` and all values in `centroids` are finite numbers
 */
template <typename T>
[[nodiscard]] size_t closest_centroid_index(const T v, std::span<const T> centroids) noexcept {
    size_t min_idx = 0;
    T      min_dist = std::numeric_limits<T>::max();
    for (size_t i = 0; i < centroids.size(); ++i) {
        const T dist = std::abs(v - centroids[i]);
        // The greater `n`, the less relative chance of observing a lower value, assuming randomized input.
        if (dist < min_dist) [[unlikely]] {
            min_idx = i;
            min_dist = dist;
        }
    }
    return min_idx;
}

} // namespace vespalib::quant
