// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <new>
#include <atomic>
#include <cassert>
#include <cstdio>
#include <vespamalloc/util/osmem.h>
#include <thread>

extern "C" void MallocRecurseOnSuspend(bool recurse) __attribute__ ((noinline));

namespace vespamalloc {

#define VESPA_DLL_EXPORT __attribute__ ((visibility("default")))

#define NELEMS(a) sizeof(a)/sizeof(a[0])

#define NUM_SIZE_CLASSES 32   // Max 64G

static constexpr uint32_t NUM_THREADS = 16384;

#define UNUSED(a)
#ifdef ENABLE_DEBUG
#define DEBUG(a) a
#else
#define DEBUG(a)
#endif

#ifndef PARANOID_LEVEL
#define PARANOID_LEVEL 0
#endif

#if (PARANOID_LEVEL >= 0)
#define PARANOID_CHECK0(a) a
#else
#define PARANOID_CHECK0(a)
#endif

#if (PARANOID_LEVEL >= 1)
#define PARANOID_CHECK1(a) a
#else
#define PARANOID_CHECK1(a)
#endif

#if (PARANOID_LEVEL >= 2)
#define PARANOID_CHECK2(a) a
#else
#define PARANOID_CHECK2(a)
#endif

#if (PARANOID_LEVEL >= 3)
#define PARANOID_CHECK3(a) a
#else
#define PARANOID_CHECK3(a)
#endif

using OSMemory = MmapMemory;
using SizeClassT = int;

constexpr size_t ALWAYS_REUSE_LIMIT = 0x100000ul;

inline constexpr int
msbIdx(uint64_t v) {
    return (sizeof(v) * 8 - 1) - __builtin_clzl(v);
}

template<size_t MinClassSizeC>
class CommonT {
public:
    static constexpr size_t MAX_ALIGN = 0x200000ul;
    enum {
        MinClassSize = MinClassSizeC
    };
    static constexpr SizeClassT sizeClass(size_t sz) noexcept {
        SizeClassT tmp(msbIdx(sz - 1) - (MinClassSizeC - 1));
        return (sz <= (1 << MinClassSizeC)) ? 0 : tmp;
    }
    static constexpr size_t classSize(SizeClassT sc) noexcept { return (size_t(1) << (sc + MinClassSizeC)); }
};

class Mutex {
public:
    Mutex() : _mutex(), _use(false) { }
    ~Mutex() { quit(); }
    void lock();
    void unlock();
    static void addThread()      { _threadCount.fetch_add(1); }
    static void subThread()      { _threadCount.fetch_sub(1); }
    static void stopRecursion()  { _stopRecursion = true; }
    static void allowRecursion() { _stopRecursion = false; }
    void init();
    void quit();
private:
    static std::atomic<uint32_t> _threadCount;
    static bool _stopRecursion;
    Mutex(const Mutex &org);
    Mutex &operator=(const Mutex &org);
    pthread_mutex_t _mutex;
    bool _use;
};

class Guard {
public:
    Guard(Mutex & m);
    ~Guard() { _mutex->unlock(); }
private:
    Mutex *_mutex;
};

class IAllocator {
public:
    virtual ~IAllocator() {}
    virtual bool initThisThread() = 0;
    virtual bool quitThisThread() = 0;
    virtual void enableThreadSupport() = 0;
    virtual void setReturnAddressStop(const void *returnAddressStop) = 0;
    virtual size_t getMaxNumThreads() const = 0;
};

void info();
void logBigBlock(const void *ptr, size_t exact, size_t adjusted, size_t gross) __attribute__((noinline));
void logStackTrace() __attribute__((noinline));

#define ASSERT_STACKTRACE(a) { \
    if ( __builtin_expect(!(a), false) ) {  \
        vespamalloc::logStackTrace();       \
        assert(a);                          \
    }                                       \
}

extern FILE * _G_logFile;
extern size_t _G_bigBlockLimit;

}

