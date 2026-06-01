// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <mutex>

namespace vespalib {

/*
 * Class used to track large transient memory allocations, e.g. when flushing
 * attribute vectors.
 */
class TransientMemoryTracker {
    static std::mutex _mutex;
    static uint64_t   _generation;
    static size_t     _total_transient_memory;
    size_t            _transient_memory;

public:
    using Lock = std::unique_lock<std::mutex>;
    struct TotalTransientMemoryAndGeneration {
        size_t   _total_transient_memory;
        uint64_t _generation;
    };
    TransientMemoryTracker() noexcept;
    TransientMemoryTracker(const TransientMemoryTracker&) = delete;
    TransientMemoryTracker(TransientMemoryTracker&& rhs) noexcept;
    ~TransientMemoryTracker();
    TransientMemoryTracker& operator=(const TransientMemoryTracker&) = delete;
    TransientMemoryTracker& operator=(TransientMemoryTracker&& rhs) noexcept;
    static Lock get_lock() { return Lock(_mutex); }
    void set_transient_memory(size_t value);
    void set_transient_memory(Lock lock, size_t value);
    void swap(TransientMemoryTracker& rhs) noexcept;
    static TotalTransientMemoryAndGeneration get_total_transient_memory();
};

} // namespace vespalib
