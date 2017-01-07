// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/threadlist.h>

namespace vespamalloc {

template <typename MemBlockPtrT, typename ThreadStatT>
ThreadListT<MemBlockPtrT, ThreadStatT>::ThreadListT(AllocPool & pool) :
    _isThreaded(false),
    _threadCount(0),
    _threadCountAccum(0),
    _allocPool(pool)
{
    for (size_t i = 0; i < getMaxNumThreads(); i++) {
        _threadVector[i].setPool(_allocPool);
    }
}

template <typename MemBlockPtrT, typename ThreadStatT>
ThreadListT<MemBlockPtrT, ThreadStatT>::~ThreadListT()
{
}

template <typename MemBlockPtrT, typename ThreadStatT>
void ThreadListT<MemBlockPtrT, ThreadStatT>::info(FILE * os, size_t level)
{
    size_t peakThreads(0);
    size_t activeThreads(0);
    for (size_t i(0); i < getMaxNumThreads(); i++) {
        const ThreadPool & thread = _threadVector[i];
        if (thread.isActive()) {
            activeThreads++;
            if ( ! ThreadStatT::isDummy()) {
                fprintf(os, "Thread #%ld = pid # %d\n", i, thread.osThreadId());
                if (thread.isUsed()) {
                    thread.info(os, level, _allocPool.dataSegment());
                }
            }
            peakThreads = i;
        }
    }
    fprintf(os, "#%ld active threads. Peak threads #%ld\n", activeThreads, peakThreads);
}

template <typename MemBlockPtrT, typename ThreadStatT>
bool ThreadListT<MemBlockPtrT, ThreadStatT>::quitThisThread()
{
    ThreadPool & tp = getCurrent();
    tp.quit();
    Atomic::postDec(&_threadCount);
    return true;
}

template <typename MemBlockPtrT, typename ThreadStatT>
bool ThreadListT<MemBlockPtrT, ThreadStatT>::initThisThread()
{
    bool retval(true);
    Atomic::postInc(&_threadCount);
    size_t lidAccum = Atomic::postInc(&_threadCountAccum);
    long localId(-1);
    for(size_t i = 0; (localId < 0) && (i < getMaxNumThreads()); i++) {
        ThreadPool & tp = _threadVector[i];
        if (tp.grabAvailable()) {
            localId = i;
        }
    }
    assert(localId >= 0);
    _myPool = &_threadVector[localId];
    assert(getThreadId() == size_t(localId));

    getCurrent().init(lidAccum);

    return retval;
}
template <typename MemBlockPtrT, typename ThreadStatT>
__thread ThreadPoolT<MemBlockPtrT, ThreadStatT> * ThreadListT<MemBlockPtrT, ThreadStatT>::_myPool TLS_LINKAGE = NULL;

}
