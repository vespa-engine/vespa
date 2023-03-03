// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ref_counted.h"
#include <cassert>

namespace vespalib {

void
enable_ref_counted::internal_addref() const noexcept
{
    // relaxed because:
    // the thread obtaining the new reference already has a reference
    auto prev = _refs.fetch_add(1, std::memory_order_relaxed);
    assert(prev > 0);
}

void
enable_ref_counted::internal_subref() const noexcept
{
    // release because:
    // our changes to the object must be visible to the deleter
    auto prev = _refs.fetch_sub(1, std::memory_order_release);
    assert(prev > 0);
    if (prev == 1) {
        // acquire because:
        // we need to see all object changes before deleting it
        std::atomic_thread_fence(std::memory_order_acquire);
        delete this;
    }
}

int32_t
enable_ref_counted::count_refs() const noexcept {
    auto result = _refs.load(std::memory_order_relaxed);
    assert(result > 0);
    return result;
}

enable_ref_counted::~enable_ref_counted() noexcept
{
    // protect against early/double delete and memory overwrites
    assert(_refs.load(std::memory_order_relaxed) == 0);
    assert(_guard == MAGIC);
    _guard = 0;
}

}
