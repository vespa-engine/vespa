// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include "datasegment.h"
#include "allocchunk.h"
#include "globalpool.h"
#include "threadpool.h"
#include "threadlist.h"
#include "threadproxy.h"

namespace vespamalloc {

template <typename MemBlockPtrT, typename ThreadListT>
class MemoryManager : public IAllocator
{
public:
    MemoryManager(size_t logLimitAtStart);
    ~MemoryManager() override;
    bool initThisThread() override;
    bool quitThisThread() override;
    void enableThreadSupport() override;
    void setReturnAddressStop(const void * returnAddressStop) override {
        MemBlockPtrT::Stack::setStopAddress(returnAddressStop);
    }
    size_t getMaxNumThreads() const override { return _threadList.getMaxNumThreads(); }

    void *malloc(size_t sz);
    void *malloc(size_t sz, std::align_val_t);
    void *realloc(void *oldPtr, size_t sz);
    void free(void *ptr) {
        freeSC(ptr, _segment.sizeClass(ptr));
    }
    void free(void *ptr, size_t sz) {
        freeSC(ptr, MemBlockPtrT::sizeClass(MemBlockPtrT::adjustSize(sz)));
    }
    void free(void *ptr, size_t sz, std::align_val_t alignment) {
        freeSC(ptr, MemBlockPtrT::sizeClass(MemBlockPtrT::adjustSize(sz, alignment)));
    }
    size_t getMinSizeForAlignment(size_t align, size_t sz) const { return MemBlockPtrT::getMinSizeForAlignment(align, sz); }
    size_t sizeClass(const void *ptr) const { return _segment.sizeClass(ptr); }
    size_t usable_size(void *ptr) const {
        return MemBlockPtrT::usable_size(ptr, _segment.getMaxSize(ptr));
    }

    void *calloc(size_t nelm, size_t esz) {
        void * ptr = malloc(nelm * esz);
        if (ptr) {
            memset(ptr, 0, nelm * esz);
        }
        return ptr;
    }

    void info(FILE * os, size_t level=0) __attribute__ ((noinline));

    void setupSegmentLog(size_t bigMemLogLevel, size_t bigLimit, size_t bigIncrement, size_t allocs2Show) {
        _segment.setupLog(bigMemLogLevel, bigLimit, bigIncrement, allocs2Show);
    }
    void setupLog(size_t prAllocLimit) {
        _prAllocLimit = prAllocLimit;
    }
    void setParams(size_t threadCacheLimit) {
        _threadList.setParams(threadCacheLimit);
        _allocPool.setParams(threadCacheLimit);
    }
    const DataSegment<MemBlockPtrT> & dataSegment() const { return _segment; }
private:
    void freeSC(void *ptr, SizeClassT sc);
    void crash() __attribute__((noinline));;
    typedef AllocPoolT<MemBlockPtrT> AllocPool;
    typedef typename ThreadListT::ThreadPool  ThreadPool;
    size_t                     _prAllocLimit;
    DataSegment<MemBlockPtrT>  _segment;
    AllocPool                  _allocPool;
    ThreadListT                _threadList;
};

template <typename MemBlockPtrT, typename ThreadListT>
MemoryManager<MemBlockPtrT, ThreadListT>::MemoryManager(size_t logLimitAtStart) :
    IAllocator(),
    _prAllocLimit(logLimitAtStart),
    _segment(),
    _allocPool(_segment),
    _threadList(_allocPool)
{
    setAllocatorForThreads(this);
    initThisThread();
    Mutex::allowRecursion();
}

template <typename MemBlockPtrT, typename ThreadListT>
MemoryManager<MemBlockPtrT, ThreadListT>::~MemoryManager() = default;

template <typename MemBlockPtrT, typename ThreadListT>
bool MemoryManager<MemBlockPtrT, ThreadListT>::initThisThread()
{
    bool retval(_threadList.initThisThread());
    if ( retval ) {
        // ThreadPool & tp = _threadList.getCurrent();
        // tp.init(_threadList.getThreadId());
    } else {
        abort();
    }
    return retval;
}

template <typename MemBlockPtrT, typename ThreadListT>
bool MemoryManager<MemBlockPtrT, ThreadListT>::quitThisThread()
{
    return _threadList.quitThisThread();
}

template <typename MemBlockPtrT, typename ThreadListT>
void MemoryManager<MemBlockPtrT, ThreadListT>::enableThreadSupport()
{
    _segment.enableThreadSupport();
    _allocPool.enableThreadSupport();
    _threadList.enableThreadSupport();
}

template <typename MemBlockPtrT, typename ThreadListT>
void MemoryManager<MemBlockPtrT, ThreadListT>::crash()
{
    fprintf(stderr, "vespamalloc detected unrecoverable error.\n");
#if 0
    if (_invalidMemLogLevel > 0) {
        static size_t numRecurse=0;
        if (numRecurse++ == 0) {
            MemBlockPtrT::dumpInfo(_invalidMemLogLevel);
        }
        numRecurse--;
    }
    sleep(1);
#else
    abort();
#endif
}

template <typename MemBlockPtrT, typename ThreadListT>
void MemoryManager<MemBlockPtrT, ThreadListT>::info(FILE * os, size_t level)
{
    fprintf(os, "DataSegment at %p(%ld), AllocPool at %p(%ld), ThreadList at %p(%ld)\n",
            &_segment, sizeof(_segment), &_allocPool, sizeof(_allocPool),
            &_threadList, sizeof(_threadList));
    _segment.info(os, level);
    _allocPool.info(os, level);
    _threadList.info(os, level);
    fflush(os);
}

template <typename MemBlockPtrT, typename ThreadListT>
void * MemoryManager<MemBlockPtrT, ThreadListT>::malloc(size_t sz)
{
    MemBlockPtrT mem;
    ThreadPool & tp = _threadList.getCurrent();
    tp.malloc(mem.adjustSize(sz), mem);
    if (!mem.validFree()) {
        fprintf(stderr, "Memory %p(%ld) has been tampered with after free.\n", mem.ptr(), mem.size());
        crash();
    }
    mem.setExact(sz);
    mem.alloc(_prAllocLimit<=mem.adjustSize(sz));
    return mem.ptr();
}

template <typename MemBlockPtrT, typename ThreadListT>
void * MemoryManager<MemBlockPtrT, ThreadListT>::malloc(size_t sz, std::align_val_t alignment)
{
    MemBlockPtrT mem;
    ThreadPool & tp = _threadList.getCurrent();
    tp.malloc(mem.adjustSize(sz, alignment), mem);
    if (!mem.validFree()) {
        fprintf(stderr, "Memory %p(%ld) has been tampered with after free.\n", mem.ptr(), mem.size());
        crash();
    }
    mem.setExact(sz, alignment);
    mem.alloc(_prAllocLimit<=mem.adjustSize(sz, alignment));
    return mem.ptr();
}

template <typename MemBlockPtrT, typename ThreadListT>
void MemoryManager<MemBlockPtrT, ThreadListT>::freeSC(void *ptr, SizeClassT sc)
{
    if (MemBlockPtrT::verifySizeClass(sc)) {
        ThreadPool & tp = _threadList.getCurrent();
        MemBlockPtrT mem(ptr);
        mem.readjustAlignment(_segment);
        if (mem.validAlloc()) {
            mem.free();
            tp.free(mem, sc);
        } else if (mem.validFree()) {
            fprintf(stderr, "Already deleted %p(%ld).\n", mem.ptr(), mem.size());
            // MemBlockPtrT::dumpInfo(_doubleDeleteLogLevel);
            crash();
        } else {
            fprintf(stderr, "Someone has tamper with my pre/post signatures of my memoryblock %p(%ld).\n", mem.ptr(), mem.size());
            crash();
        }
    } else {
        fprintf(stderr, "%p not allocated here, can not be freed\n", ptr);
        crash();
    }
}

template <typename MemBlockPtrT, typename ThreadListT>
void * MemoryManager<MemBlockPtrT, ThreadListT>::realloc(void *oldPtr, size_t sz)
{
    void *ptr(NULL);
    if (oldPtr) {
        MemBlockPtrT mem(oldPtr);
        mem.readjustAlignment(_segment);
        if (! mem.validAlloc()) {
            fprintf(stderr, "Someone has tamper with my pre/post signatures of my memoryblock %p(%ld).\n", mem.ptr(), mem.size());
            crash();
        }
        SizeClassT sc(_segment.sizeClass(oldPtr));
        if (sc >= 0) {
            size_t oldSz(_segment.getMaxSize(oldPtr));
            if (sz > oldSz) {
                ptr = malloc(sz);
                if (ptr) {
                    memcpy(ptr, oldPtr, oldSz);
                    free(oldPtr);
                }
            } else {
                mem.setExact(sz);
                ptr = oldPtr;
            }
        } else {
            ptr = malloc(sz);
            if (ptr) {
                memcpy(ptr, oldPtr, sz);
            }
        }
    } else {
        ptr = malloc(sz);
    }
    PARANOID_CHECK2( { MemBlockPtrT mem(ptr); mem.readjustAlignment(_segment); if (! mem.validAlloc()) { crash(); } });
    return ptr;
}

}
