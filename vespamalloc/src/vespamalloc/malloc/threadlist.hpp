// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "threadlist.h"
#include <malloc.h>

namespace vespamalloc {

namespace {
    const char * VESPA_MALLOC_MMAP_THRESHOLD = "VESPA_MALLOC_MMAP_THRESHOLD";
}

template <typename MemBlockPtrT, typename ThreadStatT>
ThreadListT<MemBlockPtrT, ThreadStatT>::ThreadListT(AllocPool & allocPool, MMapPool & mmapPool) :
    _isThreaded(false),
    _threadCount(0),
    _threadCountAccum(0),
    _allocPool(allocPool),
    _mmapPool(mmapPool)
{
    const char * mmapThresholdS = getenv(VESPA_MALLOC_MMAP_THRESHOLD);
    int mmapThreshold = (mmapThresholdS != nullptr)
            ? strtol(mmapThresholdS, nullptr, 0)
            : MMAP_LIMIT_DEFAULT;
    for (size_t i = 0; i < getMaxNumThreads(); i++) {
        auto & thread = _threadVector[i];
        thread.setPool(_allocPool, _mmapPool);
        thread.mallopt(M_MMAP_THRESHOLD, mmapThreshold);
    }
}

template <typename MemBlockPtrT, typename ThreadStatT>
ThreadListT<MemBlockPtrT, ThreadStatT>::~ThreadListT() = default;

template <typename MemBlockPtrT, typename ThreadStatT>
void ThreadListT<MemBlockPtrT, ThreadStatT>::info(FILE * os, size_t level)
{
    size_t peakThreads(0);
    size_t activeThreads(0);
    for (size_t i(0); i < getMaxNumThreads(); i++) {
        const ThreadPool & thread = _threadVector[i];
        if (thread.isActive()) {
            activeThreads++;
            peakThreads = i;
        }
    }
    fprintf(os, "#%ld active threads. Peak threads #%ld. %u threads created in total.\n",
            activeThreads, peakThreads, _threadCountAccum.load());
    if ((level > 1) && ! ThreadStatT::isDummy()) {
        for (SizeClassT sc(0); sc < NUM_SIZE_CLASSES; sc++) {
            _allocPool.dataSegment().infoThread(os, level, 0, sc, _threadCountAccum.load() + 1);
        }
    }
    for (size_t i(0); i < getMaxNumThreads(); i++) {
        const ThreadPool & thread = _threadVector[i];
        if (thread.isActive()) {
            if ( ! ThreadStatT::isDummy()) {
                if (thread.isUsed()) {
                    fprintf(os, "Thread #%u = pid # %d\n", thread.threadId(), thread.osThreadId());
                    thread.info(os, level, _allocPool.dataSegment());
                }
            }
        }
    }
}

template <typename MemBlockPtrT, typename ThreadStatT>
bool ThreadListT<MemBlockPtrT, ThreadStatT>::quitThisThread()
{
    ThreadPool & tp = getCurrent();
    tp.quit();
    _threadCount.fetch_sub(1);
    return true;
}

template <typename MemBlockPtrT, typename ThreadStatT>
bool ThreadListT<MemBlockPtrT, ThreadStatT>::initThisThread()
{
    bool retval(true);
    _threadCount.fetch_add(1);
    uint32_t lidAccum = _threadCountAccum.fetch_add(1);
    long localId(-1);
    for(size_t i = 0; (localId < 0) && (i < getMaxNumThreads()); i++) {
        ThreadPool & tp = _threadVector[i];
        if (tp.grabAvailable()) {
            localId = i;
        }
    }
    ASSERT_STACKTRACE(localId >= 0);
    ASSERT_STACKTRACE(size_t(localId) < getMaxNumThreads());
    _myPool = &_threadVector[localId];
    ASSERT_STACKTRACE(getThreadId() == size_t(localId));
    ASSERT_STACKTRACE(lidAccum < 0xffffffffu);
    getCurrent().init(lidAccum+1);

    return retval;
}

}
