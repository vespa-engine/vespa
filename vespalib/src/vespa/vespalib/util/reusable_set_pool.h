// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reusable_set.h"
#include "reusable_set_handle.h"
#include "memoryusage.h"

#include <vector>
#include <mutex>

namespace vespalib {

/**
 * A resource pool for ReusableSet instances.
 * Note that the pool should have a guaranteed lifetime
 * that is longer than any Handle retrieved from the pool.
 **/
class ReusableSetPool
{
    using RSUP = std::unique_ptr<ReusableSet>;
    using Guard = std::lock_guard<std::mutex>;
    std::vector<RSUP> _lru_stack;
    mutable std::mutex _lock;
    size_t _reuse_count;
    size_t _create_count;
    MemoryUsage _total_memory;
    const size_t _min_size;
    const size_t _grow_percent;

    ReusableSetPool(const ReusableSetPool &) = delete;
    ReusableSetPool& operator= (const ReusableSetPool &) = delete;

public:
    ReusableSetPool()
      : _lru_stack(), _lock(),
        _reuse_count(0), _create_count(0),
        _total_memory(),
        _min_size(248), _grow_percent(20)
    {
        _total_memory.incAllocatedBytes(sizeof(ReusableSetPool));
    }

    /** Create or re-use a set with (at least) the given size. */
    ReusableSetHandle get(size_t size) {
        Guard guard(_lock);
        size_t last_used_size = 0;
        while (! _lru_stack.empty()) {
            RSUP r = std::move(_lru_stack.back());
            _lru_stack.pop_back();
            if (r->capacity() >= size) {
                r->clear();
                ++_reuse_count;
                _total_memory.incUsedBytes(r->memory_usage());
                return ReusableSetHandle(std::move(r), *this);
            }
            _total_memory.decAllocatedBytes(r->memory_usage());
            last_used_size = std::max(last_used_size, r->capacity());
        }
        double grow_factor = (1.0 + _grow_percent / 100.0);
        last_used_size *= grow_factor;
        size_t at_least_size = std::max(_min_size, last_used_size);
        RSUP r = std::make_unique<ReusableSet>(std::max(at_least_size, size));
        _total_memory.incAllocatedBytes(r->memory_usage());
        ++_create_count;
        _total_memory.incUsedBytes(r->memory_usage());
        return ReusableSetHandle(std::move(r), *this);
    }

    /** Return a ReusableSet to the pool. */
    void reuse(RSUP used) {
        Guard guard(_lock);
        _total_memory.decUsedBytes(used->memory_usage());
        _lru_stack.push_back(std::move(used));
    }

    // for unit testing and statistics
    size_t reuse_count() const { return _reuse_count; }
    size_t create_count() const { return _create_count; }
    MemoryUsage memory_usage() const {
        Guard guard(_lock);
        return _total_memory;
    }
};

} // namespace
