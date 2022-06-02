// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageframework/generic/thread/threadpool.h>
#include <vespa/fastos/thread.h>

namespace storage::framework::defaultimplementation {

class ThreadImpl;

struct ThreadPoolImpl final : public ThreadPool
{
    FastOS_ThreadPool          _backendThreadPool;
    std::vector<ThreadImpl*>   _threads;
    mutable std::mutex         _threadVectorLock;
    Clock                    & _clock;
    bool                       _stopping;

public:
    ThreadPoolImpl(Clock&);
    ~ThreadPoolImpl() override;

    Thread::UP startThread(Runnable&, vespalib::stringref id, vespalib::duration waitTime,
                           vespalib::duration maxProcessTime, int ticksBeforeWait,
                           std::optional<vespalib::CpuUsage::Category> cpu_category) override;
    void visitThreads(ThreadVisitor&) const override;

    void registerThread(ThreadImpl&);
    void unregisterThread(ThreadImpl&);
    FastOS_ThreadPool& getThreadPool() { return _backendThreadPool; }
    Clock& getClock() { return _clock; }
};

}
