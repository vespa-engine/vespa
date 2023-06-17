// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <dlfcn.h>
#include <cerrno>
#include <new>
#include <cstdlib>
#include <malloc.h>

class CreateAllocator
{
public:
    static constexpr unsigned CONSTRUCTED = 0x192A3B4C;
    static constexpr unsigned DESTRUCTED = 0xd1d2d3d4;
    CreateAllocator() : _initialized(CONSTRUCTED) {
        vespamalloc::createAllocator();
    }
    ~CreateAllocator() {
        assert(_initialized == CONSTRUCTED);
        _initialized = DESTRUCTED;
    }
private:
    unsigned _initialized;
};

static CreateAllocator _CreateAllocator __attribute__ ((init_priority (543)));

void* operator new(std::size_t sz)
{
    void * ptr(vespamalloc::createAllocator()->malloc(sz));
    if (ptr == nullptr) {
        throw std::bad_alloc();
    }
    return ptr;
}

void* operator new[](std::size_t sz)
{
    return ::operator new(sz);
}

void* operator new(std::size_t sz, const std::nothrow_t&) noexcept {
    return vespamalloc::_GmemP->malloc(sz);
}
void* operator new[](std::size_t sz, const std::nothrow_t&) noexcept {
    return vespamalloc::_GmemP->malloc(sz);
}

void operator delete(void* ptr) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr); }
}
void operator delete[](void* ptr) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr); }
}
void operator delete(void* ptr, const std::nothrow_t&) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr); }
}
void operator delete[](void* ptr, const std::nothrow_t&) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr); }
}
void operator delete(void* ptr, std::size_t sz) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr, sz); }
}
void operator delete[](void* ptr, std::size_t sz) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr, sz); }
}
void operator delete(void* ptr, std::size_t sz, const std::nothrow_t&) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr, sz); }
}
void operator delete[](void* ptr, std::size_t sz, const std::nothrow_t&) noexcept {
    if (ptr) { vespamalloc::_GmemP->free(ptr, sz); }
}

/*
 * Below are overloads taking alignment into account too.
 * Due to allocation being power of 2 up to huge page size (2M)
 * alignment will always be satisfied. size will always be larger or equal to alignment.
 */
void* operator new(std::size_t sz, std::align_val_t alignment) {
    return vespamalloc::_GmemP->malloc(sz, alignment);
}
void* operator new(std::size_t sz, std::align_val_t alignment, const std::nothrow_t&) noexcept {
    return vespamalloc::_GmemP->malloc(sz, alignment);
}
void operator delete(void* ptr , std::align_val_t) noexcept {
    return vespamalloc::_GmemP->free(ptr);
}
void operator delete(void* ptr, std::align_val_t, const std::nothrow_t&) noexcept {
    return vespamalloc::_GmemP->free(ptr);
}
void* operator new[](std::size_t sz, std::align_val_t alignment) {
    return vespamalloc::_GmemP->malloc(sz, alignment);
}
void* operator new[](std::size_t sz, std::align_val_t alignment, const std::nothrow_t&) noexcept {
    return vespamalloc::_GmemP->malloc(sz, alignment);
}
void operator delete[](void* ptr, std::align_val_t) noexcept {
    return vespamalloc::_GmemP->free(ptr);
}
void operator delete[](void* ptr, std::align_val_t, const std::nothrow_t&) noexcept {
    return vespamalloc::_GmemP->free(ptr);
}
void operator delete(void* ptr, std::size_t sz, std::align_val_t alignment) noexcept {
    return vespamalloc::_GmemP->free(ptr, sz, alignment);
}
void operator delete[](void* ptr, std::size_t sz, std::align_val_t alignment) noexcept {
    return vespamalloc::_GmemP->free(ptr, sz, alignment);
}

extern "C" {

#if __GLIBC_PREREQ(2, 33)
struct mallinfo2 mallinfo2() __THROW __attribute__((visibility ("default")));
struct mallinfo2 mallinfo2() __THROW {
    struct mallinfo2 info;
    info.arena = vespamalloc::_GmemP->dataSegment().dataSize();
    info.ordblks = 0;
    info.smblks = 0;
    info.hblkhd = vespamalloc::_GmemP->mmapPool().getNumMappings();
    info.hblks = vespamalloc::_GmemP->mmapPool().getMmappedBytes();
    info.usmblks = 0;
    info.fsmblks = 0;
    info.fordblks = vespamalloc::_GmemP->dataSegment().freeSize();
    info.uordblks = info.arena + info.hblks - info.fordblks;
    info.keepcost = 0;
    return info;
}
#else
struct mallinfo mallinfo() __THROW __attribute__((visibility ("default")));
struct mallinfo mallinfo() __THROW {
    struct mallinfo info;
    info.arena = (vespamalloc::_GmemP->dataSegment().dataSize() >> 20); // Note reporting in 1M blocks
    info.ordblks = 0;
    info.smblks = 0;
    info.hblkhd = vespamalloc::_GmemP->mmapPool().getNumMappings();
    info.hblks = (vespamalloc::_GmemP->mmapPool().getMmappedBytes() >> 20);
    info.usmblks = 0;
    info.fsmblks = 0;
    info.fordblks = (vespamalloc::_GmemP->dataSegment().freeSize() >> 20);
    info.uordblks = info.arena + info.hblks - info.fordblks;
    info.keepcost = 0;
    return info;
}
#endif

int mallopt(int param, int value) throw() __attribute((visibility("default")));
int mallopt(int param, int value) throw() {
    return vespamalloc::createAllocator()->mallopt(param, value);
}

void * malloc(size_t sz) __attribute((visibility("default")));
void * malloc(size_t sz) {
    return vespamalloc::createAllocator()->malloc(sz);
}

void * calloc(size_t nelem, size_t esz) __attribute((visibility("default")));
void * calloc(size_t nelem, size_t esz)
{
    return vespamalloc::createAllocator()->calloc(nelem, esz);
}

void * realloc(void * ptr, size_t sz) __attribute((visibility("default")));
void * realloc(void * ptr, size_t sz)
{
    return vespamalloc::createAllocator()->realloc(ptr, sz);
}

void * reallocarray(void * ptr, size_t nemb, size_t elemSize) __THROW __attribute__((visibility ("default")));
void * reallocarray(void * ptr, size_t nemb, size_t elemSize) __THROW
{
    size_t sz = nemb * elemSize;
    if (nemb != 0 && (sz/nemb != elemSize)) {
        errno = ENOMEM;
        return nullptr;
    }
    return vespamalloc::createAllocator()->realloc(ptr, sz);
}

void* memalign(size_t align, size_t sz) __THROW __attribute__((visibility ("default")));
void* memalign(size_t align_in, size_t sz) __THROW
{
    size_t align = (align_in == 0) ? 1 : 1ul << vespamalloc::msbIdx(align_in*2 - 1);
    size_t align_1(align - 1);
    void * ptr = vespamalloc::_GmemP->malloc(vespamalloc::_GmemP->getMinSizeForAlignment(align, sz));
    return (void *) ((size_t(ptr) + align_1) & ~align_1);
}

void *aligned_alloc (size_t align, size_t sz) __THROW __attribute__((visibility ("default")));
void *aligned_alloc (size_t align_in, size_t sz_in) __THROW
{
    size_t align = (align_in == 0) ? 1 : 1ul << vespamalloc::msbIdx(align_in*2 - 1);
    size_t align_1(align - 1);
    size_t sz = ((sz_in - 1) + align) & ~align_1;
    void * ptr = vespamalloc::_GmemP->malloc(vespamalloc::_GmemP->getMinSizeForAlignment(align, sz));
    return (void *) ((size_t(ptr) + align_1) & ~align_1);
}

int posix_memalign(void** ptr, size_t align, size_t sz) __THROW __nonnull((1)) __attribute__((visibility ("default")));
int posix_memalign(void** ptr, size_t align, size_t sz) __THROW
{
    int retval(0);
    if (((align % sizeof(void*)) != 0) ||
        ((align & (align - 1)) != 0) ||
        (align == 0)) {
        retval = EINVAL;
    } else {
        void* result = memalign(align, sz);
        if (result) {
            *ptr = result;
        } else {
            retval = ENOMEM;
        }
    }
    return retval;
}

void *valloc(size_t size) __THROW __attribute__((visibility ("default")));
void *valloc(size_t size) __THROW
{
  return memalign(sysconf(_SC_PAGESIZE),size);
}

void free(void * ptr) __attribute__((visibility ("default")));
void free(void * ptr) {
    if (ptr) { vespamalloc::_GmemP->free(ptr); }
}

size_t malloc_usable_size(void *) __THROW __attribute__((visibility ("default")));
size_t malloc_usable_size (void * ptr) __THROW  {
    return (ptr) ? vespamalloc::_GmemP->usable_size(ptr) : 0;
}

#define ALIAS(x) __attribute__ ((weak, alias (x), visibility ("default")))

void* __libc_malloc(size_t sz)                       __THROW __attribute__((leaf, malloc, alloc_size(1))) ALIAS("malloc");
void* __libc_realloc(void* ptr, size_t sz)           __THROW __attribute__((leaf, malloc, alloc_size(2))) ALIAS("realloc");
void* __libc_reallocarray(void* ptr, size_t nemb, size_t sz) __THROW __attribute__((leaf, malloc, alloc_size(2,3))) ALIAS("reallocarray");
void* __libc_calloc(size_t n, size_t sz)             __THROW __attribute__((leaf, malloc, alloc_size(1,2))) ALIAS("calloc");
void  __libc_free(void* ptr)                         __THROW __attribute__((leaf)) ALIAS("free");
size_t  __libc_malloc_usable_size(void *ptr)         __THROW  ALIAS("malloc_usable_size");

#if __GLIBC_PREREQ(2, 34)
void* __libc_memalign(size_t align, size_t s)        __THROW __attribute__((leaf, malloc, alloc_align(1), alloc_size(2))) ALIAS("memalign");
#else
void* __libc_memalign(size_t align, size_t s)        __THROW __attribute__((leaf, malloc, alloc_size(2))) ALIAS("memalign");
#endif

int   __posix_memalign(void** r, size_t a, size_t s) __THROW __nonnull((1)) ALIAS("posix_memalign") __attribute((leaf));

#if __GLIBC_PREREQ(2, 33)
struct mallinfo2 __libc_mallinfo2()                  __THROW  ALIAS("mallinfo2");
#else
struct mallinfo __libc_mallinfo()                    __THROW  ALIAS("mallinfo");
#endif
#undef ALIAS

}
