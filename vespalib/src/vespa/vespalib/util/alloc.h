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
    using UP = std::unique_ptr<MemoryAllocator>;
    MemoryAllocator(const MemoryAllocator &) = delete;
    MemoryAllocator & operator = (const MemoryAllocator &) = delete;
    MemoryAllocator() { }
    virtual ~MemoryAllocator() { }
    virtual void * alloc(size_t sz) = 0;
    virtual void free(void * buf, size_t sz) = 0;
};

class HeapAllocator : public MemoryAllocator {
public:
    void * alloc(size_t sz) override;
    void free(void * buf, size_t sz) override;
    static void * salloc(size_t sz);
    static void sfree(void * buf, size_t sz);
};

class AlignedHeapAllocator : public HeapAllocator {
public:
    AlignedHeapAllocator(size_t alignment) : _alignment(alignment) { }
    void * alloc(size_t sz) override;
private:
    size_t _alignment;
};

class MMapAllocator : public MemoryAllocator {
public:
    enum {HUGEPAGE_SIZE=0x200000};
    void * alloc(size_t sz) override;
    void free(void * buf, size_t sz) override;
    static void * salloc(size_t sz);
    static void sfree(void * buf, size_t sz);
    static size_t roundUpToHugePages(size_t sz) {
        return (sz+(HUGEPAGE_SIZE-1)) & ~(HUGEPAGE_SIZE-1);
    }
};

class AutoAllocator : public MemoryAllocator {
public:
    AutoAllocator(size_t mmapLimit) : _mmapLimit(mmapLimit) { }
    void * alloc(size_t sz) override;
    void free(void * buf, size_t sz) override;
private:
    size_t roundUpToHugePages(size_t sz) {
        return (_mmapLimit >= MMapAllocator::HUGEPAGE_SIZE)
            ? MMapAllocator::roundUpToHugePages(sz)
            : sz;
    }
    bool useMMap(size_t sz) { return (sz >= _mmapLimit); }
    size_t _mmapLimit;
};

}

inline size_t roundUp2inN(size_t minimum) {
    return 2ul << Optimized::msbIdx(minimum - 1);
}
/**
 * This is an allocated buffer interface that does not accept virtual inheritance.
**/
class Alloc
{
public:
    size_t size() const { return _sz; }
    void * get() { return _buf; }
    const void * get() const { return _buf; }
    void * operator -> () { return _buf; }
    const void * operator -> () const { return _buf; }
    Alloc(const Alloc &) = delete;
    Alloc & operator = (const Alloc &) = delete;
protected:
    Alloc(Alloc && rhs) :
        _buf(rhs._buf),
        _sz(rhs._sz)
    {
        rhs._buf = nullptr;
        rhs._sz = 0;
    }
    Alloc & operator=(Alloc && rhs) {
        if (this != & rhs) {
            internalSwap(rhs);
        }
        return *this;
    }
    Alloc(void * buf, size_t sz) : _buf(buf), _sz(sz) { }
    ~Alloc() { _buf = 0; }
    void internalSwap(Alloc & rhs) {
        std::swap(_buf, rhs._buf);
        std::swap(_sz, rhs._sz);
    }
private:
    void * _buf;
    size_t _sz;
};

class HeapAlloc : public Alloc
{
public:
    typedef std::unique_ptr<HeapAlloc> UP;
    HeapAlloc() : Alloc(NULL, 0) { }
    HeapAlloc(size_t sz) : Alloc(HeapAlloc::alloc(sz), sz) { }
    ~HeapAlloc() { HeapAlloc::free(get(), size()); }
    HeapAlloc(HeapAlloc && rhs) : Alloc(std::move(rhs)) { }

    HeapAlloc & operator=(HeapAlloc && rhs) {
        Alloc::operator=(std::move(rhs));
        return *this;
    }
    void swap(HeapAlloc & rhs) { internalSwap(rhs); }
public:
    static void * alloc(size_t sz) { return (sz > 0) ? malloc(sz) : 0; }
    static void free(void * buf, size_t sz) { (void) sz; if (buf) { ::free(buf); } }
};

class AlignedHeapAlloc : public Alloc
{
public:
    typedef std::unique_ptr<AlignedHeapAlloc> UP;
    AlignedHeapAlloc() : Alloc(NULL, 0) { }
    AlignedHeapAlloc(size_t sz, size_t alignment)
        : Alloc(AlignedHeapAlloc::alloc(sz, alignment), sz) { }
    AlignedHeapAlloc(AlignedHeapAlloc && rhs) : Alloc(std::move(rhs)) { }

    AlignedHeapAlloc & operator=(AlignedHeapAlloc && rhs) {
        Alloc::operator=(std::move(rhs));
        return *this;
    }
    ~AlignedHeapAlloc() { AlignedHeapAlloc::free(get(), size()); }
    void swap(AlignedHeapAlloc & rhs) { internalSwap(rhs); }
public:
    static void * alloc(size_t sz, size_t alignment);
    static void free(void * buf, size_t sz) { (void) sz; if (buf) { ::free(buf); } }
};


class MMapAlloc : public Alloc
{
public:
    enum {HUGEPAGE_SIZE=0x200000};
    typedef std::unique_ptr<MMapAlloc> UP;
    MMapAlloc() : Alloc(NULL, 0) { }
    MMapAlloc(size_t sz) : Alloc(MMapAlloc::alloc(sz), sz) { }
    MMapAlloc(MMapAlloc && rhs) : Alloc(std::move(rhs)) { }

    MMapAlloc & operator=(MMapAlloc && rhs) {
        Alloc::operator=(std::move(rhs));
        return *this;
    }
    ~MMapAlloc() { MMapAlloc::free(get(), size()); }
    void swap(MMapAlloc & rhs) { internalSwap(rhs); }
public:
    static void * alloc(size_t sz);
    static void free(void * buf, size_t sz);
};

// Alignment requirement is != 0, use posix_memalign
template <size_t Alignment>
struct ChooseHeapAlloc
{
    static inline void* alloc(size_t sz) {
        return AlignedHeapAlloc::alloc(sz, Alignment);
    }
};

// No alignment required, use regular malloc
template <>
struct ChooseHeapAlloc<0>
{
    static inline void* alloc(size_t sz) {
        return HeapAlloc::alloc(sz);
    }
};

/**
 * Optional alignment is assumed to be <= system page size, since mmap
 * is always used when size is above limit.
 */

template <size_t Lim=MMapAlloc::HUGEPAGE_SIZE, size_t Alignment=0>
class AutoAlloc : public Alloc
{
public:
    typedef std::unique_ptr<AutoAlloc> UP;
    typedef vespalib::LinkedPtr<AutoAlloc> LP;
    AutoAlloc() : Alloc(NULL, 0) { }
    AutoAlloc(size_t sz)
        : Alloc(useMMap(sz)
                    ? MMapAlloc::alloc(roundUpToHugePages(sz))
                    : ChooseHeapAlloc<Alignment>::alloc(sz),
                useMMap(sz)
                    ? roundUpToHugePages(sz)
                    : sz)
    { }
    AutoAlloc(AutoAlloc && rhs) : Alloc(std::move(rhs)) { }

    AutoAlloc & operator=(AutoAlloc && rhs) {
        Alloc::operator=(std::move(rhs));
        return *this;
    }

    ~AutoAlloc() {
        if (useMMap(size())) {
            MMapAlloc::free(get(), size());
        } else {
            HeapAlloc::free(get(), size());
        }
    }
    void swap(AutoAlloc & rhs) { internalSwap(rhs); }
private:
    static size_t roundUpToHugePages(size_t sz) {
        return (Lim >= MMapAlloc::HUGEPAGE_SIZE)
            ? (sz+(MMapAlloc::HUGEPAGE_SIZE-1)) & ~(MMapAlloc::HUGEPAGE_SIZE-1)
            : sz;
    }
    static bool useMMap(size_t sz) { return (sz >= Lim); }
};

template <size_t Lim>
inline void swap(AutoAlloc<Lim> & a, AutoAlloc<Lim> & b) { a.swap(b); }

inline void swap(HeapAlloc & a, HeapAlloc & b) { a.swap(b); }
inline void swap(AlignedHeapAlloc & a, AlignedHeapAlloc & b) { a.swap(b); }
inline void swap(MMapAlloc & a, MMapAlloc & b) { a.swap(b); }

typedef AutoAlloc<> DefaultAlloc;

}

