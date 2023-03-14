// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ref_counted.h"
#include <cassert>

namespace vespalib {

void
enable_ref_counted::internal_addref(uint32_t cnt) const noexcept
{
    // relaxed because:
    // the thread obtaining the new reference already has a reference
    auto prev = _refs.fetch_add(cnt, std::memory_order_relaxed);
    assert(prev > 0);
    assert(_guard == MAGIC);
}

void
enable_ref_counted::internal_subref(uint32_t cnt, [[maybe_unused]] uint32_t reserve) const noexcept
{
    assert(_guard == MAGIC);
    // release because:
    // our changes to the object must be visible to the deleter
    //
    // acquire because:
    // we need to see all object changes before deleting it
    auto prev = _refs.fetch_sub(cnt, std::memory_order_acq_rel);
    assert(prev >= (reserve + cnt));
    if (prev == cnt) {
        // not using conditional atomic thread fence since thread
        // sanitizer does not support it.
        delete this;
    }
}

uint32_t
enable_ref_counted::count_refs() const noexcept {
    auto result = _refs.load(std::memory_order_relaxed);
    assert(result > 0);
    assert(_guard == MAGIC);
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
