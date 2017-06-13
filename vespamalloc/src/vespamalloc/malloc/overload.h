// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <dlfcn.h>
#include <errno.h>
#include <new>
#include <stdlib.h>

class CreateAllocator
{
public:
    CreateAllocator() : _initialized(0x192A3B4C) {
        vespamalloc::createAllocator();
    }
private:
    unsigned _initialized;
};

static CreateAllocator _CreateAllocator __attribute__ ((init_priority (543)));

#if 1 // Only until we get on to a new C++14 compiler
void operator delete(void* ptr, std::size_t sz) noexcept __attribute__((visibility ("default")));
void operator delete[](void* ptr, std::size_t sz) noexcept __attribute__((visibility ("default")));
void operator delete(void* ptr, std::size_t sz, const std::nothrow_t&) noexcept __attribute__((visibility ("default")));
void operator delete[](void* ptr, std::size_t sz, const std::nothrow_t&) noexcept __attribute__((visibility ("default")));
#endif

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

extern "C" {

void * malloc(size_t sz) {
    return vespamalloc::createAllocator()->malloc(sz);
}

void * calloc(size_t nelem, size_t esz)
{
    return vespamalloc::createAllocator()->calloc(nelem, esz);
}

void * realloc(void * ptr, size_t sz)
{
    return vespamalloc::createAllocator()->realloc(ptr, sz);
}

void* memalign(size_t align, size_t sz) __attribute__((visibility ("default")));
void* memalign(size_t align, size_t sz)
{
    void *ptr(nullptr);
    size_t align_1(align - 1);
    if ((align & (align_1)) == 0) {
        ptr = vespamalloc::_GmemP->malloc(vespamalloc::_GmemP->getMinSizeForAlignment(align, sz));
        ptr = (void *) ((size_t(ptr) + align_1) & ~align_1);
    }
    return ptr;
}

int posix_memalign(void** ptr, size_t align, size_t sz) __THROW __attribute__((visibility ("default")));

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

void *valloc(size_t size) __attribute__((visibility ("default")));
void *valloc(size_t size)
{
  return memalign(sysconf(_SC_PAGESIZE),size);
}


void free(void * ptr) {
    if (ptr) { vespamalloc::_GmemP->free(ptr); }
}

#define ALIAS(x) __attribute__ ((weak, alias (x), visibility ("default")))
void cfree(void *)                                   ALIAS("free");
void* __libc_malloc(size_t sz)                       ALIAS("malloc");
void  __libc_free(void* ptr)                         ALIAS("free");
void* __libc_realloc(void* ptr, size_t sz)           ALIAS("realloc");
void* __libc_calloc(size_t n, size_t sz)             ALIAS("calloc");
void  __libc_cfree(void* ptr)                        ALIAS("cfree");
void* __libc_memalign(size_t align, size_t s)        ALIAS("memalign");
int   __posix_memalign(void** r, size_t a, size_t s) ALIAS("posix_memalign");
#undef ALIAS

#if 0
#include <dlfcn.h>

typedef void * (*dlopen_function) (const char *filename, int flag);

extern "C" VESPA_DLL_EXPORT void * local_dlopen(const char *filename, int flag) __asm__("dlopen");

VESPA_DLL_EXPORT void * local_dlopen(const char *filename, int flag)
{
    // A pointer to the library version of dlopen.
    static dlopen_function real_dlopen = nullptr;

    const char * dlopenName = "dlopen";

    if (real_dlopen == nullptr) {
        real_dlopen = (dlopen_function) dlsym (RTLD_NEXT, dlopenName);
        if (real_dlopen == nullptr) {
            fprintf (stderr, "Could not find the dlopen function!\n");
            abort();
        }
    }
    //flag = (flag & ~RTLD_DEEPBIND & ~RTLD_NOW) | RTLD_LAZY;
    //fprintf(stderr, "modified dlopen('%s', %0x)\n", filename, flag);
    void * handle = real_dlopen(filename, flag);
    fprintf(stderr, "dlopen('%s', %0x) = %p\n", filename, flag, handle);
    return handle;
}

typedef int (*dlclose_function) (void * handle);
extern "C" VESPA_DLL_EXPORT int local_dlclose(void * handle) __asm__("dlclose");
VESPA_DLL_EXPORT int local_dlclose(void * handle)
{
    // A pointer to the library version of dlclose.
    static dlclose_function real_dlclose = nullptr;

    const char * dlcloseName = "dlclose";

    if (real_dlclose == nullptr) {
        real_dlclose = (dlclose_function) dlsym (RTLD_NEXT, dlcloseName);
        if (real_dlclose == nullptr) {
            fprintf (stderr, "Could not find the dlclose function!\n");
            abort();
        }
    }
    int retval = real_dlclose(handle);
    fprintf(stderr, "dlclose(%p) = %d\n", handle, retval);
    return retval;
}

typedef void * (*dlsym_function) (void * handle, const char * symbol);
extern "C" VESPA_DLL_EXPORT void * local_dlsym(void * handle, const char * symbol) __asm__("dlsym");
VESPA_DLL_EXPORT void * local_dlsym(void * handle, const char * symbol)
{
    // A pointer to the library version of dlsym.
    static dlsym_function real_dlsym = nullptr;

    const char * dlsymName = "dlsym";

    if (real_dlsym == nullptr) {
        real_dlsym = (dlsym_function) dlvsym (RTLD_NEXT, dlsymName, "GLIBC_2.2.5");
        if (real_dlsym == nullptr) {
            fprintf (stderr, "Could not find the dlsym function!\n");
            abort();
        }
    }
    if (handle == RTLD_NEXT) {
        fprintf(stderr, "dlsym(RTLD_NEXT, %s)\n", symbol);
    } else if (handle == RTLD_DEFAULT) {
        fprintf(stderr, "dlsym(RTLD_DEFAULT, %s)\n", symbol);
    } else {
        fprintf(stderr, "dlsym(%p, %s)\n", handle, symbol);
    }
    void * retval = real_dlsym(handle, symbol);
    if (handle == RTLD_NEXT) {
        fprintf(stderr, "dlsym(RTLD_NEXT, %s) = %p\n", symbol, retval);
    } else if (handle == RTLD_DEFAULT) {
        fprintf(stderr, "dlsym(RTLD_DEFAULT, %s) = %p\n", symbol, retval);
    } else {
        fprintf(stderr, "dlsym(%p, %s) = %p\n", handle, symbol, retval);
    }
    return retval;
}

#endif

}
