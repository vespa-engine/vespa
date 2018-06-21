// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadpoolimpl.h"
#include "threadimpl.h"
#include <vespa/vespalib/util/exceptions.h>
#include <thread>
#include <vespa/log/log.h>
LOG_SETUP(".storageframework.thread_pool_impl");

using namespace std::chrono_literals;
using vespalib::IllegalStateException;

namespace storage::framework::defaultimplementation {

ThreadPoolImpl::ThreadPoolImpl(Clock& clock)
    : _backendThreadPool(512 * 1024),
      _clock(clock),
      _stopping(false)
{ }

ThreadPoolImpl::~ThreadPoolImpl()
{
    {
        vespalib::LockGuard lock(_threadVectorLock);
        _stopping = true;
        for (ThreadImpl * thread : _threads) {
            thread->interrupt();
        }
        for (ThreadImpl * thread : _threads) {
            thread->join();
        }
    }
    for (uint32_t i=0; true; i+=10) {
        {
            vespalib::LockGuard lock(_threadVectorLock);
            if (_threads.empty()) break;
        }
        if (i > 1000) {
            fprintf(stderr, "Failed to kill thread pool. Threads won't die. (And if allowing thread pool object"
                            " to be deleted this will create a segfault later)\n");
            LOG_ABORT("should not be reached");
        }
        std::this_thread::sleep_for(10ms);
    }
    _backendThreadPool.Close();
}

Thread::UP
ThreadPoolImpl::startThread(Runnable& runnable, vespalib::stringref id, uint64_t waitTimeMs,
                            uint64_t maxProcessTime, int ticksBeforeWait)
{
    vespalib::LockGuard lock(_threadVectorLock);
    if (_stopping) {
        throw IllegalStateException("Threadpool is stopping", VESPA_STRLOC);
    }
    ThreadImpl* ti;
    Thread::UP t(ti = new ThreadImpl(*this, runnable, id, waitTimeMs, maxProcessTime, ticksBeforeWait));
    _threads.push_back(ti);
    return t;
}

void
ThreadPoolImpl::visitThreads(ThreadVisitor& visitor) const
{
    vespalib::LockGuard lock(_threadVectorLock);
    for (const ThreadImpl * thread : _threads) {
        visitor.visitThread(thread->getId(), thread->getProperties(), thread->getTickData());
    }
}

void
ThreadPoolImpl::unregisterThread(ThreadImpl& t)
{
    vespalib::LockGuard lock(_threadVectorLock);
    std::vector<ThreadImpl*> threads;
    threads.reserve(_threads.size());
    for (ThreadImpl * thread : _threads) {
        if (thread != &t) {
            threads.push_back(thread);
        }
    }
    _threads.swap(threads);
}

}
