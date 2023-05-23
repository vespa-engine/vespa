// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_allocator_observer.h"
#include <iostream>

namespace vespalib::alloc::test {

std::ostream&
operator<<(std::ostream &os, const MemoryAllocatorObserver::Stats &stats)
{
    os << "{alloc_cnt=" << stats.alloc_cnt << ", free_cnt=" << stats.free_cnt << "}";
    return os;
}

MemoryAllocatorObserver::MemoryAllocatorObserver(Stats &stats)
    : MemoryAllocator(),
      _stats(stats),
      _backing_allocator(alloc::MemoryAllocator::select_allocator(HUGEPAGE_SIZE, 0))
{
}
MemoryAllocatorObserver::~MemoryAllocatorObserver() = default;

PtrAndSize
MemoryAllocatorObserver::alloc(size_t sz) const
{
    ++_stats.alloc_cnt;
    return _backing_allocator->alloc(sz);
}


void
MemoryAllocatorObserver::free(PtrAndSize alloc) const
{
    ++_stats.free_cnt;
    _backing_allocator->free(alloc);
}

size_t
MemoryAllocatorObserver::resize_inplace(PtrAndSize current, size_t newSize) const
{
    return _backing_allocator->resize_inplace(current, newSize);
}

}
