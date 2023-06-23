// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/threadpool.h>
#include <malloc.h>

namespace vespamalloc {

namespace {
    size_t
    sanitizeMMapThreshold(int threshold) {
        return std::min(MMAP_LIMIT_MAX, std::max(MMAP_LIMIT_MIN, threshold));
    }
}

template <typename MemBlockPtrT, typename ThreadStatT>
size_t ThreadPoolT<MemBlockPtrT, ThreadStatT>::_threadCacheLimit __attribute__((visibility("hidden"))) = 0x10000;

template <typename MemBlockPtrT, typename ThreadStatT>
void
ThreadPoolT<MemBlockPtrT, ThreadStatT>::info(FILE * os, size_t level, const DataSegment & ds) const {
    if (level > 0) {
        for (size_t i=0; i < NELEMS(_stat); i++) {
            const ThreadStatT & s = _stat[i];
            const AllocFree & af = _memList[i];
            if (s.isUsed()) {
                size_t localAvailCount((af._freeTo ? af._freeTo->count() : 0)
                                       + (af._allocFrom ? af._allocFrom->count() : 0));
                fprintf(os, "SC %2ld(%10ld) Local(%3ld) Alloc(%10ld), "
                        "Free(%10ld) ExchangeAlloc(%8ld), ExChangeFree(%8ld) "
                        "Returned(%8ld) ExactAlloc(%8ld)\n",
                        i, MemBlockPtrT::classSize(i), localAvailCount,
                        s.alloc(), s.free(), s.exchangeAlloc(),
                        s.exchangeFree(), s.returnFree(), s.exactAlloc());
            }
        }
    }
    if (level > 2) {
        fprintf(os, "BlockList:%ld,%ld,%ld\n", NELEMS(_stat), sizeof(_stat), sizeof(_stat[0]));
        size_t sum(0), sumLocal(0);
        for (size_t i=0; i < NELEMS(_stat); i++) {
            const ThreadStatT & s = _stat[i];
            if (s.isUsed()) {
                fprintf(os, "Allocated Blocks SC %2ld(%10ld): ", i, MemBlockPtrT::classSize(i));
                size_t allocCount = ds.infoThread(os, level, threadId(), i);
                const AllocFree & af = _memList[i];
                size_t localAvailCount((af._freeTo ? af._freeTo->count() : 0)
                                       + (af._allocFrom ? af._allocFrom->count() : 0));
                sum += allocCount*MemBlockPtrT::classSize(i);
                sumLocal += localAvailCount*MemBlockPtrT::classSize(i);
                fprintf(os, " Total used(%ld + %ld = %ld(%ld)).\n",
                        allocCount, localAvailCount, localAvailCount+allocCount,
                        (localAvailCount+allocCount)*MemBlockPtrT::classSize(i));
            }
        }
        fprintf(os, "Sum = (%ld + %ld) = %ld\n", sum, sumLocal, sum+sumLocal);
    }
}

template <typename MemBlockPtrT, typename ThreadStatT >
void
ThreadPoolT<MemBlockPtrT, ThreadStatT>::
mallocHelper(size_t exactSize,
             SizeClassT sc,
             typename ThreadPoolT<MemBlockPtrT, ThreadStatT>::AllocFree & af,
             MemBlockPtrT & mem)
{
    if (!af._freeTo->empty()) {
        af.swap();
        af._allocFrom->sub(mem);
        PARANOID_CHECK2( if (!mem.ptr()) { *(int *)0 = 0; } );
    } else {
        if ( ! alwaysReuse(sc) ) {
            af._allocFrom = _allocPool->exchangeAlloc(sc, af._allocFrom);
            _stat[sc].incExchangeAlloc();
            if (af._allocFrom) {
                af._allocFrom->sub(mem);
                PARANOID_CHECK2( if (!mem.ptr()) { *(int *)1 = 1; } );
            } else {
                PARANOID_CHECK2( *(int *)2 = 2; );
            }
        } else {
            if (exactSize > _mmapLimit) {
                mem = MemBlockPtrT(_mmapPool->mmap(MemBlockPtrT::classSize(sc)), MemBlockPtrT::classSize(sc));
                // The below settings are to allow the sanity checks conducted at the call site to succeed
                mem.setExact(exactSize);
                mem.free();
            } else {
                af._allocFrom = _allocPool->exactAlloc(exactSize, sc, af._allocFrom);
                _stat[sc].incExactAlloc();
                if (af._allocFrom) {
                    af._allocFrom->sub(mem);
                    PARANOID_CHECK2(if (!mem.ptr()) { *(int *) 3 = 3; });
                } else {
                    PARANOID_CHECK2(*(int *) 4 = 4;);
                }
            }
        }
    }
}

template <typename MemBlockPtrT, typename ThreadStatT >
ThreadPoolT<MemBlockPtrT, ThreadStatT>::ThreadPoolT() :
    _allocPool(nullptr),
    _mmapPool(nullptr),
    _mmapLimit(MMAP_LIMIT_MAX),
    _threadId(0),
    _osThreadId(0)
{
}

template <typename MemBlockPtrT, typename ThreadStatT >
ThreadPoolT<MemBlockPtrT, ThreadStatT>::~ThreadPoolT() = default;

template <typename MemBlockPtrT, typename ThreadStatT >
int ThreadPoolT<MemBlockPtrT, ThreadStatT>::mallopt(int param, int value) {
    if (param == M_MMAP_THRESHOLD) {
        _mmapLimit = sanitizeMMapThreshold(value);
        return 1;
    }
    return 0;
}

template <typename MemBlockPtrT, typename ThreadStatT >
void ThreadPoolT<MemBlockPtrT, ThreadStatT>::malloc(size_t sz, MemBlockPtrT & mem)
{
    SizeClassT sc = MemBlockPtrT::sizeClass(sz);
    AllocFree & af = _memList[sc];
    af._allocFrom->sub(mem);
    if ( !mem.ptr()) {
        mallocHelper(sz, sc, af, mem);
    }
    PARANOID_CHECK2(if (!mem.validFree()) { *(int *)1 = 1; } );
    _stat[sc].incAlloc();
    mem.setThreadId(_threadId);
    PARANOID_CHECK2(if (af._allocFrom->count() > ChunkSList::NumBlocks) { *(int *)1 = 1; } );
    PARANOID_CHECK2(if (af._freeTo->count() > ChunkSList::NumBlocks) { *(int *)1 = 1; } );
    PARANOID_CHECK2(if (af._freeTo->full()) { *(int *)1 = 1; } );
    PARANOID_CHECK2(if (af._allocFrom->full()) { *(int *)1 = 1; } );
}

template <typename MemBlockPtrT, typename ThreadStatT >
void ThreadPoolT<MemBlockPtrT, ThreadStatT>::free(MemBlockPtrT mem, SizeClassT sc)
{
    PARANOID_CHECK2(if (!mem.validFree()) { *(int *)1 = 1; } );
    AllocFree & af = _memList[sc];
    const size_t cs(MemBlockPtrT::classSize(sc));
    if ((af._allocFrom->count()+1)*cs < _threadCacheLimit) {
        if ( ! af._allocFrom->full() ) {
            af._allocFrom->add(mem);
        } else {
            af._freeTo->add(mem);
            if (af._freeTo->full()) {
                af._freeTo = _allocPool->exchangeFree(sc, af._freeTo);
                _stat[sc].incExchangeFree();
            }
        }
    } else if (cs < _threadCacheLimit) {
        af._freeTo->add(mem);
        if (af._freeTo->count()*cs > _threadCacheLimit) {
            af._freeTo = _allocPool->exchangeFree(sc, af._freeTo);
            _stat[sc].incExchangeFree();
        }
    } else if ( !alwaysReuse(sc) ) {
        af._freeTo->add(mem);
        af._freeTo = _allocPool->exchangeFree(sc, af._freeTo);
        _stat[sc].incExchangeFree();
    } else {
        af._freeTo->add(mem);
        af._freeTo = _allocPool->returnMemory(sc, af._freeTo);
        _stat[sc].incReturnFree();
    }

    _stat[sc].incFree();
    PARANOID_CHECK2(if (af._allocFrom->count() > ChunkSList::NumBlocks) { *(int *)1 = 1; } );
    PARANOID_CHECK2(if (af._freeTo->count() > ChunkSList::NumBlocks) { *(int *)1 = 1; } );
    PARANOID_CHECK2(if (af._freeTo->full()) { *(int *)1 = 1; } );
}

template <typename MemBlockPtrT, typename ThreadStatT >
bool ThreadPoolT<MemBlockPtrT, ThreadStatT>::isActive() const
{
    return (_osThreadId != 0);
}

template <typename MemBlockPtrT, typename ThreadStatT >
bool ThreadPoolT<MemBlockPtrT, ThreadStatT>::isUsed() const
{
    return isActive() && hasActuallyBeenUsed();
}

template <typename MemBlockPtrT, typename ThreadStatT >
bool ThreadPoolT<MemBlockPtrT, ThreadStatT>::hasActuallyBeenUsed() const
{
    bool used(false);
    for (size_t i=0; !used && (i < NELEMS(_memList)); i++) {
        used = (_memList[i]._allocFrom != nullptr
                && !_memList[i]._allocFrom->empty()
                && !_memList[i]._freeTo->full());
    }
    return used;
}

template <typename MemBlockPtrT, typename ThreadStatT >
void ThreadPoolT<MemBlockPtrT, ThreadStatT>::init(int thrId)
{
    setThreadId(thrId);
    ASSERT_STACKTRACE(_osThreadId.load(std::memory_order_relaxed) == -1);
    _osThreadId = pthread_self();
    for (size_t i=0; (i < NELEMS(_memList)); i++) {
        _memList[i].init(*_allocPool, i);
    }
    // printf("OsThreadId = %lx, threadId = %x\n", _osThreadId, _threadId);
}

template <typename MemBlockPtrT, typename ThreadStatT >
void ThreadPoolT<MemBlockPtrT, ThreadStatT>::setParams(size_t threadCacheLimit)
{
    _threadCacheLimit = threadCacheLimit;
}

template <typename MemBlockPtrT, typename ThreadStatT >
bool ThreadPoolT<MemBlockPtrT, ThreadStatT>::grabAvailable()
{
    if (_osThreadId.load(std::memory_order_relaxed) == 0) {
        ssize_t expected = 0;
        if (_osThreadId.compare_exchange_strong(expected, -1)) {
            return true;
        }
    }
    return false;
}

}
