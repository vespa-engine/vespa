// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "threadpool.h"

namespace vespamalloc {

#ifdef __PIC__
    #define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("initial-exec")))
#else
    #define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("local-exec")))
#endif

template <typename MemBlockPtrT, typename ThreadStatT>
class ThreadListT
{
public:
    typedef ThreadPoolT<MemBlockPtrT, ThreadStatT > ThreadPool;
    typedef AllocPoolT<MemBlockPtrT> AllocPool;
    ThreadListT(AllocPool & pool);
    ~ThreadListT();
    void setParams(size_t alwayReuseLimit, size_t threadCacheLimit) {
        ThreadPool::setParams(alwayReuseLimit, threadCacheLimit);
    }
    bool quitThisThread();
    bool initThisThread();
    ThreadPool & getCurrent()  { return *_myPool; }
    size_t getThreadId() const { return (_myPool - _threadVector); }
    void enableThreadSupport() {
        _isThreaded.test_and_set();
    }

    void info(FILE * os, size_t level=0);
    size_t getMaxNumThreads() const { return NELEMS(_threadVector); }
private:
    ThreadListT(const ThreadListT & tl);
    ThreadListT & operator = (const ThreadListT & tl);
    std::atomic_flag           _isThreaded;
    std::atomic<uint32_t>      _threadCount;
    std::atomic<uint32_t>      _threadCountAccum;
    ThreadPool                 _threadVector[NUM_THREADS];
    AllocPoolT<MemBlockPtrT> & _allocPool;
    static thread_local ThreadPool * _myPool TLS_LINKAGE;
};

template <typename MemBlockPtrT, typename ThreadStatT>
thread_local ThreadPoolT<MemBlockPtrT, ThreadStatT> * ThreadListT<MemBlockPtrT, ThreadStatT>::_myPool TLS_LINKAGE = nullptr;

}
