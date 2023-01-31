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

template <typename MemBlockPtrT>
class MemBlockInfoT final : public segment::IMemBlockInfo {
public:
    MemBlockInfoT(void *ptr) : _mem(ptr, 0, false) { }
    bool allocated() const override { return _mem.allocated(); }
    uint32_t threadId() const override { return _mem.threadId(); }
    void info(FILE * os, int level) const override { _mem.info(os, level); }
    uint32_t callStackLen() const override { return _mem.callStackLen(); }
    const StackEntry * callStack() const override { return _mem.callStack(); }
private:
    MemBlockPtrT _mem;
};

template <typename MemBlockPtrT, typename ThreadListT>
class MemoryManager : public IAllocator, public segment::IHelper
{
    using DataSegment = segment::DataSegment;
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
    size_t classSize(SizeClassT sc) const override { return MemBlockPtrT::classSize(sc); }
    void dumpInfo(int level) const override { MemBlockPtrT::dumpInfo(level); }
    std::unique_ptr<segment::IMemBlockInfo>
    createMemblockInfo(void * ptr) const override {
        return std::make_unique<MemBlockInfoT<MemBlockPtrT>>(ptr);
    }

    int mallopt(int param, int value);
    void *malloc(size_t sz);
    void *malloc(size_t sz, std::align_val_t);
    void *realloc(void *oldPtr, size_t sz);
    void free(void *ptr) {
        if (_segment.containsPtr(ptr)) {
            freeSC(ptr, _segment.sizeClass(ptr));
        } else {
            _mmapPool.unmap(MemBlockPtrT(ptr).rawPtr());
        }
    }
    void free(void *ptr, size_t sz) {
        if (_segment.containsPtr(ptr)) {
            freeSC(ptr, MemBlockPtrT::sizeClass(MemBlockPtrT::adjustSize(sz)));
        } else {
            _mmapPool.unmap(MemBlockPtrT(ptr).rawPtr());
        }
    }
    void free(void *ptr, size_t sz, std::align_val_t alignment) {
        if (_segment.containsPtr(ptr)) {
            freeSC(ptr, MemBlockPtrT::sizeClass(MemBlockPtrT::adjustSize(sz, alignment)));
        } else {
            _mmapPool.unmap(MemBlockPtrT(ptr).rawPtr());
        }
    }
    size_t getMinSizeForAlignment(size_t align, size_t sz) const { return MemBlockPtrT::getMinSizeForAlignment(align, sz); }
    size_t sizeClass(const void *ptr) const { return _segment.sizeClass(ptr); }
    size_t usable_size(void *ptr) const {
        return MemBlockPtrT::usable_size(ptr, _segment);
    }

    void * calloc(size_t nelm, size_t esz) {
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
    const DataSegment & dataSegment() const { return _segment; }
    const MMapPool & mmapPool() const { return _mmapPool; }
private:
    void freeSC(void *ptr, SizeClassT sc);
    void crash() __attribute__((noinline));
    using AllocPool = AllocPoolT<MemBlockPtrT>;
    using ThreadPool = typename ThreadListT::ThreadPool;
    size_t       _prAllocLimit;
    DataSegment  _segment;
    AllocPool    _allocPool;
    MMapPool     _mmapPool;
    ThreadListT  _threadList;
};

template <typename MemBlockPtrT, typename ThreadListT>
MemoryManager<MemBlockPtrT, ThreadListT>::MemoryManager(size_t logLimitAtStart) :
    IAllocator(),
    _prAllocLimit(logLimitAtStart),
    _segment(*this),
    _allocPool(_segment),
    _mmapPool(),
    _threadList(_allocPool, _mmapPool)
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
    logStackTrace();
    abort();
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
int MemoryManager<MemBlockPtrT, ThreadListT>::mallopt(int param, int value) {
    return _threadList.getCurrent().mallopt(param, value);
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
    if (oldPtr == nullptr) return malloc(sz);
    if ( ! _segment.containsPtr(oldPtr)) {
        void * ptr = malloc(sz);
        size_t oldBlockSize = _mmapPool.get_size(MemBlockPtrT(oldPtr).rawPtr());
        memcpy(ptr, oldPtr, MemBlockPtrT::unAdjustSize(oldBlockSize));
        _mmapPool.unmap(MemBlockPtrT(oldPtr).rawPtr());
        return ptr;
    }

    MemBlockPtrT mem(oldPtr);
    mem.readjustAlignment(_segment);
    if (! mem.validAlloc()) {
        fprintf(stderr, "Someone has tampered with the pre/post signatures of my memoryblock %p(%ld).\n", mem.ptr(), mem.size());
        crash();
    }

    SizeClassT sc(_segment.sizeClass(oldPtr));
    void * ptr;
    if (sc >= 0) {
        size_t oldSz(_segment.getMaxSize<MemBlockPtrT>(oldPtr));
        if (sz > oldSz) {
            ptr = malloc(sz);
            memcpy(ptr, oldPtr, oldSz);
            free(oldPtr);
        } else {
            mem.setExact(sz);
            ptr = oldPtr;
        }
    } else {
        ptr = malloc(sz);
        memcpy(ptr, oldPtr, sz);
    }
    PARANOID_CHECK2( { MemBlockPtrT mem(ptr); mem.readjustAlignment(_segment); if (! mem.validAlloc()) { crash(); } });
    return ptr;
}

}
