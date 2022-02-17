// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "optimized.h"
#include "memory_allocator.h"
#include <memory>

namespace vespalib::alloc {

/**
 * This represents an allocation.
 * It can be created, moved, swapped.
 * The allocation strategy is decided upon creation.
 * It can also create create additional allocations with the same allocation strategy.
**/
class Alloc
{
private:
    using PtrAndSize = std::pair<void *, size_t>;
public:
    size_t size() const { return _alloc.second; }
    void * get() { return _alloc.first; }
    const void * get() const { return _alloc.first; }
    void * operator -> () { return _alloc.first; }
    const void * operator -> () const { return _alloc.first; }
    /*
     * If possible the allocations will be resized. If it was possible it will return true
     * And you have an area that can be accessed up to the new size.
     * The old buffer is unmodified up to the new size.
     * This is thread safe and at no point will data in the buffer be invalid.
     * @param newSize The desired new size
     * @return true if successful.
     */
    bool resize_inplace(size_t newSize);
    Alloc(const Alloc &) = delete;
    Alloc & operator = (const Alloc &) = delete;
    Alloc(Alloc && rhs) noexcept :
        _alloc(rhs._alloc),
        _allocator(rhs._allocator)
    {
        rhs.clear();
    }
    Alloc & operator=(Alloc && rhs) noexcept {
        if (this != & rhs) {
            if (_alloc.first != nullptr) {
                _allocator->free(_alloc);
            }
            _alloc = rhs._alloc;
            _allocator = rhs._allocator;
            rhs.clear();
        }
        return *this;
    }
    Alloc() noexcept : _alloc(nullptr, 0), _allocator(nullptr) { }
    ~Alloc() {
        if (_alloc.first != nullptr) {
            _allocator->free(_alloc);
            _alloc.first = nullptr;
        }
    }
    void swap(Alloc & rhs) noexcept {
        std::swap(_alloc, rhs._alloc);
        std::swap(_allocator, rhs._allocator);
    }
    void reset() {
        if (_alloc.first != nullptr) {
            _allocator->free(_alloc);
            _alloc.first = nullptr;
            _alloc.second = 0u;
        }
    }
    Alloc create(size_t sz) const noexcept {
        return (sz == 0) ? Alloc(_allocator) : Alloc(_allocator, sz);
    }

    static Alloc allocAlignedHeap(size_t sz, size_t alignment);
    static Alloc allocHeap(size_t sz=0);
    static Alloc allocMMap(size_t sz=0);
    /**
     * Optional alignment is assumed to be <= system page size, since mmap
     * is always used when size is above limit.
     */
    static Alloc alloc(size_t sz) noexcept;
    static Alloc alloc_aligned(size_t sz, size_t alignment) noexcept;
    static Alloc alloc(size_t sz, size_t mmapLimit, size_t alignment=0) noexcept;
    static Alloc alloc() noexcept;
    static Alloc alloc_with_allocator(const MemoryAllocator* allocator) noexcept;
private:
    Alloc(const MemoryAllocator * allocator, size_t sz) noexcept
        : _alloc(allocator->alloc(sz)),
          _allocator(allocator)
    {
    }
    Alloc(const MemoryAllocator * allocator) noexcept
        : _alloc(nullptr, 0),
          _allocator(allocator)
    { }
    void clear() {
        _alloc.first = nullptr;
        _alloc.second = 0;
        _allocator = nullptr;
    }
    PtrAndSize              _alloc;
    const MemoryAllocator * _allocator;
};

}

namespace vespalib {

/// Rounds up to the closest number that is a power of 2
inline size_t
roundUp2inN(size_t minimum) {
    return 2ul << Optimized::msbIdx(minimum - 1);
}

/// Rounds minElems up to the closest number where minElems*elemSize is a power of 2
inline size_t
roundUp2inN(size_t minElems, size_t elemSize) {
    return roundUp2inN(minElems * elemSize)/elemSize;
}

template <typename T>
size_t
roundUp2inN(size_t elems) {
    return roundUp2inN(elems, sizeof(T));
}

}
