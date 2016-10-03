// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h>
#include <algorithm>
#include <vespa/vespalib/util/linkedptr.h>
#include <vespa/vespalib/util/optimized.h>

namespace vespalib {

namespace alloc {

class MemoryAllocator {
public:
    enum {HUGEPAGE_SIZE=0x200000};
    using UP = std::unique_ptr<MemoryAllocator>;
    MemoryAllocator(const MemoryAllocator &) = delete;
    MemoryAllocator & operator = (const MemoryAllocator &) = delete;
    MemoryAllocator() { }
    virtual ~MemoryAllocator() { }
    virtual void * alloc(size_t sz) const = 0;
    virtual void free(void * buf, size_t sz) const = 0;
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
public:
    using MemoryAllocator = alloc::MemoryAllocator;
    size_t size() const { return _sz; }
    void * get() { return _buf; }
    const void * get() const { return _buf; }
    void * operator -> () { return _buf; }
    const void * operator -> () const { return _buf; }
    Alloc(const Alloc &) = delete;
    Alloc & operator = (const Alloc &) = delete;
    Alloc(Alloc && rhs) :
        _buf(rhs._buf),
        _sz(rhs._sz),
        _allocator(rhs._allocator)
    {
        rhs._buf = nullptr;
        rhs._sz = 0;
        rhs._allocator = 0;
    }
    Alloc & operator=(Alloc && rhs) {
        if (this != & rhs) {
            swap(rhs);
        }
        return *this;
    }
    Alloc() : _buf(nullptr), _sz(0), _allocator(nullptr) { }
    Alloc(const MemoryAllocator * allocator, size_t sz) : _buf(allocator->alloc(sz)), _sz(sz), _allocator(allocator) { }
    ~Alloc() { 
        if (_buf != nullptr) {
            _allocator->free(_buf, _sz);
            _buf = nullptr;
        }
    }
    void swap(Alloc & rhs) {
        std::swap(_buf, rhs._buf);
        std::swap(_sz, rhs._sz);
        std::swap(_allocator, rhs._allocator);
    }
    Alloc create(size_t sz) const {
        return Alloc(_allocator, sz);
    }
protected:
    void                  * _buf;
    size_t                  _sz;
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
