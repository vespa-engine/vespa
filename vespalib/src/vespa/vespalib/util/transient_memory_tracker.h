// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <mutex>

namespace vespalib {

/*
 * Class used to track large transient memory allocations, e.g. when flushing
 * attribute vectors.
 *
 * The memory usage sampler is expected to first get total transient memory from this class,
 * sample /proc/self/statm and get total transient memory again. If the generation changed
 * then we might get glitches in the calculated value for non-transient memory due to a
 * race between sampling of /proc/self/statm and tracking of transient memory.
 */
class TransientMemoryTracker {
    static std::mutex _mutex;
    static size_t     _total_transient_memory;
    size_t            _transient_memory;

public:
    using Lock = std::unique_lock<std::mutex>;
    TransientMemoryTracker() noexcept;
    TransientMemoryTracker(const TransientMemoryTracker&) = delete;
    TransientMemoryTracker(TransientMemoryTracker&& rhs) noexcept;
    ~TransientMemoryTracker();
    TransientMemoryTracker& operator=(const TransientMemoryTracker&) = delete;
    TransientMemoryTracker& operator=(TransientMemoryTracker&& rhs) noexcept;
    [[nodiscard]] static Lock acquire_lock() noexcept { return Lock(_mutex); }
    // The following member function should not be called while holding the lock.
    void set_transient_memory(size_t value) noexcept;
    void set_transient_memory(Lock lock, size_t value) noexcept;
    void swap(TransientMemoryTracker& rhs) noexcept;
    [[nodiscard]] static size_t get_total_transient_memory(Lock lock) noexcept;
};

} // namespace vespalib
