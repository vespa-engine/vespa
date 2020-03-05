// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reusable_set.h"
#include "reusable_set_handle.h"

#include <mutex>

namespace vespalib {

class ReusableSetPool
{
    using RSUP = std::unique_ptr<ReusableSet>;
    using Guard = std::lock_guard<std::mutex>;
    std::vector<RSUP> _lru_stack;
    std::mutex _lock;
    size_t _reuse_count;
    size_t _create_count;
    

    ReusableSetPool(const ReusableSetPool &) = delete;
    ReusableSetPool& operator= (const ReusableSetPool &) = delete;

public:
    ReusableSetPool() : _lru_stack(), _lock(), _reuse_count(0), _create_count(0) {}

    ReusableSetHandle get(size_t size) {
        Guard guard(_lock);
        while (! _lru_stack.empty()) {
            RSUP r = std::move(_lru_stack.back());
            _lru_stack.pop_back();
            if (r->sz >= size) {
                r->clear();
		++_reuse_count;
                return ReusableSetHandle(std::move(r), *this);
            }
        }
        RSUP r = std::make_unique<ReusableSet>(std::max((size_t)250, size*2));
	++_create_count;
        return ReusableSetHandle(std::move(r), *this);
    }

    void reuse(RSUP used) {
        Guard guard(_lock);
        _lru_stack.push_back(std::move(used));
    }

    // for unit testing and statistics
    size_t reuse_count() const { return _reuse_count; }
    size_t create_count() const { return _create_count; }
};

} // namespace
