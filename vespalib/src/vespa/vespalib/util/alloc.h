// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h>
#include <algorithm>
#include <vespa/vespalib/util/optimized.h>

namespace vespalib {

namespace alloc {

class MemoryAllocator {
public:
    enum {HUGEPAGE_SIZE=0x200000};
    using UP = std::unique_ptr<MemoryAllocator>;
    using PtrAndSize = std::pair<void *, size_t>;
    MemoryAllocator(const MemoryAllocator &) = delete;
    MemoryAllocator & operator = (const MemoryAllocator &) = delete;
    MemoryAllocator() { }
    virtual ~MemoryAllocator() { }
    virtual PtrAndSize alloc(size_t sz) const = 0;
    virtual void free(PtrAndSize alloc) const = 0;
    static size_t roundUpToHugePages(size_t sz) {
        return (sz+(HUGEPAGE_SIZE-1)) & ~(HUGEPAGE_SIZE-1);
    }
};

/**
 * This represents an allocation.
 * It can be created, moved, swapped.
 * The allocation strategy is decided upon creation.
 * It can also create create additional allocations with the same allocation strategy.
**/
class Alloc
{
private:
    using PtrAndSize = MemoryAllocator::PtrAndSize;;
public:
    size_t size() const { return _alloc.second; }
    void * get() { return _alloc.first; }
    const void * get() const { return _alloc.first; }
    void * operator -> () { return _alloc.first; }
    const void * operator -> () const { return _alloc.first; }
    Alloc(const Alloc &) = delete;
    Alloc & operator = (const Alloc &) = delete;
    Alloc(Alloc && rhs) :
        _alloc(rhs._alloc),
        _allocator(rhs._allocator)
    {
        rhs.clear();
    }
    Alloc & operator=(Alloc && rhs) {
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
    Alloc() : _alloc(nullptr, 0), _allocator(nullptr) { }
    Alloc(const MemoryAllocator * allocator, size_t sz) : _alloc(allocator->alloc(sz)), _allocator(allocator) { }
    ~Alloc() { 
        if (_alloc.first != nullptr) {
            _allocator->free(_alloc);
            _alloc.first = nullptr;
        }
    }
    void swap(Alloc & rhs) {
        std::swap(_alloc, rhs._alloc);
        std::swap(_allocator, rhs._allocator);
    }
    Alloc create(size_t sz) const {
        return Alloc(_allocator, sz);
    }
private:
    void clear() {
        _alloc.first = nullptr;
        _alloc.second = 0;
        _allocator = nullptr;
    }
    PtrAndSize              _alloc;
    const MemoryAllocator * _allocator;
};

class HeapAllocFactory
{
public:
    static Alloc create(size_t sz=0);
};

class AlignedHeapAllocFactory
{
public:
    static Alloc create(size_t sz, size_t alignment);
};

class MMapAllocFactory
{
public:
    enum {HUGEPAGE_SIZE=0x200000};
    static Alloc create(size_t sz=0);
};

/**
 * Optional alignment is assumed to be <= system page size, since mmap
 * is always used when size is above limit.
 */

class AutoAllocFactory 
{
public:
    static Alloc create(size_t sz=0, size_t mmapLimit=MemoryAllocator::HUGEPAGE_SIZE, size_t alignment=0);
};

}

inline size_t roundUp2inN(size_t minimum) {
    return 2ul << Optimized::msbIdx(minimum - 1);
}

using DefaultAlloc = alloc::AutoAllocFactory;

}
