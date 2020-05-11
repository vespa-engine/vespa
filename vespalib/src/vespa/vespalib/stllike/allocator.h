// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * std compliant allocator that will use a smart allocator
 * that uses mmap prefering huge pages for large allocations.
 * This is a good fit for use with std::vector and std::deque.
 */
template <typename T>
class allocator_large {
    using PtrAndSize = alloc::MemoryAllocator::PtrAndSize;
public:
    allocator_large() : _allocator(alloc::MemoryAllocator::select_allocator()) {}
    using value_type = T;
    T * allocate(std::size_t n) {
        return static_cast<T *>(_allocator->alloc(n*sizeof(T)).first);
    }
    void deallocate(T * p, std::size_t n) {
        _allocator->free(p, n*sizeof(T));
    }
private:
    const alloc::MemoryAllocator * _allocator;
};

}
