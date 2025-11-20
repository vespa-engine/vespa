// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <atomic>

namespace storage::distributor {

// This would ideally use std::hardware_destructive_interference_size, but that
// triggers compiler warnings on GCC since a fallback value is used, which in turn
// triggers build errors for us. So use the ol' faithful 64 bytes line size...
constexpr inline size_t cache_alignment = 64;

/**
 * A very simple, thread-safe class for keeping track of estimated memory usage
 * across distributor stripes.
 *
 * In addition, the maximum observed total is tracked separately, allowing for
 * destructive periodic sampling, akin to how metric min/max is tracked.
 * Although current/max are individually atomically updated, they are not
 * updated atomically _together_. Callers should not depend on this for correctness.
 */
class alignas(cache_alignment) MemoryUsageTracker {
    // Invariant: these are always >= 0
    std::atomic<ssize_t> _bytes_total;
    std::atomic<ssize_t> _max_observed_bytes;

    friend class MemoryUsageToken;

    void add_bytes_used(uint32_t n_bytes) noexcept {
        sub_add_bytes_used(0, n_bytes);
    }
    void sub_bytes_used(uint32_t n_bytes) noexcept {
        sub_add_bytes_used(n_bytes, 0);
    }
    void sub_add_bytes_used(uint32_t old_bytes, uint32_t new_bytes) noexcept {
        const ssize_t delta = static_cast<ssize_t>(new_bytes) - static_cast<ssize_t>(old_bytes);
        const ssize_t my_before = _bytes_total.fetch_add(delta, std::memory_order_relaxed);
        const ssize_t my_after = my_before + delta;
        ssize_t cur_max = _max_observed_bytes.load(std::memory_order_relaxed);
        // This will only contend if threads are observing increasing maximums, which should
        // quickly settle. In uncontended cases this is expected to be on a cache line that
        // we already hold exclusively due to the previous fetch_add.
        while ((my_after > cur_max) &&
               !_max_observed_bytes.compare_exchange_weak(cur_max, my_after,
                                                          std::memory_order_relaxed, std::memory_order_relaxed))
        {
            // We raced, try again
        }
    }
public:
    constexpr MemoryUsageTracker() noexcept : _bytes_total(0), _max_observed_bytes(0) {}

    [[nodiscard]] size_t bytes_total() const noexcept {
        return static_cast<size_t>(_bytes_total.load(std::memory_order_relaxed));
    }

    [[nodiscard]] size_t max_observed_bytes() const noexcept {
        return static_cast<size_t>(_max_observed_bytes.load(std::memory_order_relaxed));
    }

    struct RelaxedSnapshot {
        size_t bytes_total = 0;
        size_t max_observed_bytes = 0;
    };

    // Returns a snapshot that is atomic for individual values, but not _across_ values.
    // Returned max bytes may be adjusted so that it is always >= current total bytes.
    [[nodiscard]] RelaxedSnapshot relaxed_snapshot() const noexcept {
        const size_t total = bytes_total();
        // It's possible that we race with a concurrent update, so ensure max is at least
        // as big as the sampled current total
        const size_t adj_max = std::max(max_observed_bytes(), total);
        return {total, adj_max};
    }

    void reset_max_observed_bytes() noexcept {
        _max_observed_bytes.store(0, std::memory_order_relaxed);
    }
};

} // storage::distributor
