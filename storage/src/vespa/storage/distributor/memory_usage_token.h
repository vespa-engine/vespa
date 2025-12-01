// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_usage_tracker.h"

namespace storage::distributor {

/**
 * RAII-style token that represents the caller holding a particular amount of
 * allocated memory. The amount of memory the token represents can be adjusted
 * up or down as required. Although the MemoryUsageTracker a token is bound to
 * is thread safe, individual MemoryUsageToken instances are not thread safe.
 */
class MemoryUsageToken {
    MemoryUsageTracker& _tracker;
    // Since we're limited to 2GiB max payloads, assume that u32 suffices for bytes used
    // for a single tracked operation. Operations with fan-outs are expected to use shared_ptrs
    // to Document instances etc. that are common across messages to avoid duplication.
    uint32_t _bytes_used;
public:
    MemoryUsageToken(MemoryUsageTracker& tracker, uint32_t bytes_used) noexcept
        : _tracker(tracker),
          _bytes_used(bytes_used)
    {
        _tracker.add_bytes_used(bytes_used);
    }
    ~MemoryUsageToken() {
        _tracker.sub_bytes_used(_bytes_used);
    }

    [[nodiscard]] uint32_t bytes_used() const noexcept { return _bytes_used; }

    void update(uint32_t new_usage_bytes) noexcept {
        _tracker.sub_add_bytes_used(_bytes_used, new_usage_bytes);
        _bytes_used = new_usage_bytes;
    }
};

} // storage::distributor
