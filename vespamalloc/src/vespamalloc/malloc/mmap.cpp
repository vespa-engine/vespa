// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <sys/mman.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <stdio.h>

extern "C" {

typedef void * (*mmap_function) (void *addr, size_t length, int prot, int flags, int fd, off_t offset);
typedef void * (*mmap64_function) (void *addr, size_t length, int prot, int flags, int fd, off64_t offset);
typedef int (*munmap_function) (void *addr, size_t length);

void * local_mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset) __asm__("mmap");
void * local_mmap64(void *addr, size_t length, int prot, int flags, int fd, off64_t offset) __asm__("mmap64");
int munmap(void *addr, size_t length) __asm__("munmap");

// This is a dirty prototype of an internal, yet visible method in libc that avoids
// allocations as they will cause a loop when used with vespamalloc.
void *_dl_sym (void *handle, const char *name, void *who);

static size_t getLogLimit()
{
    static size_t LogLimit = -2l;
    if (LogLimit == static_cast<size_t>(-2l)) {
        const char * s = getenv("VESPA_MMAP_BIGBLOCK_LOGLIMIT");
        if (s) {
            LogLimit = strtoul(s, NULL, 0);
        } else {
            LogLimit = -1l;
        }
    }
    return LogLimit;
}

const size_t MagicVespaMallocStartOfHeap = 0x100000000;
const size_t MagicVespaMallocStartOfHeapFilter = 0xffffffff00000000ul;


static bool isFromVespaMalloc(const void * addr)
{
    size_t v(reinterpret_cast<size_t>(addr));
    return (v & MagicVespaMallocStartOfHeapFilter) == MagicVespaMallocStartOfHeap;
}

void * local_mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset)
{
    static mmap_function real_func = NULL;
    if (real_func == NULL) {
        real_func = (mmap_function) dlsym (RTLD_NEXT, "mmap");
        if (real_func == NULL) {
            fprintf (stderr, "Could not find the mmap function!\n");
            abort();
        }
    }
    if ((length >= getLogLimit()) && !isFromVespaMalloc(addr)) {
        fprintf (stderr, "mmap requesting block of size %ld from %s\n", length, "no backtrace");
    }
    return (*real_func)(addr, length, prot, flags, fd, offset);
}

void * local_mmap64(void *addr, size_t length, int prot, int flags, int fd, off64_t offset)
{
    static mmap64_function real_func = NULL;
    if (real_func == NULL) {
        real_func = (mmap64_function) dlsym (RTLD_NEXT, "mmap64");
        if (real_func == NULL) {
            fprintf (stderr, "Could not find the mmap64 function!\n");
            abort();
        }
    }
    if (length >= getLogLimit() && !isFromVespaMalloc(addr)) {
        fprintf (stderr, "mmap requesting block of size %ld from %s\n", length, "no backtrace");
    }
    return (*real_func)(addr, length, prot, flags, fd, offset);
}

int local_munmap(void *addr, size_t length)
{
    static munmap_function real_func = NULL;
    if (real_func == NULL) {
        real_func = (munmap_function) dlsym (RTLD_NEXT, "munmap");
        if (real_func == NULL) {
            fprintf (stderr, "Could not find the munmap function!\n");
            abort();
        }
    }
    if ((length >= getLogLimit()) && !isFromVespaMalloc(addr)) {
        fprintf (stderr, "munmap releasing block of size %ld from %s\n", length, "no backtrace");
    }
    return (*real_func)(addr, length);
}

}
