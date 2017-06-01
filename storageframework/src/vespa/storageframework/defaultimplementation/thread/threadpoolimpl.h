// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageframework/generic/thread/threadpool.h>
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/util/sync.h>

namespace storage::framework::defaultimplementation {

class ThreadImpl;

struct ThreadPoolImpl : public ThreadPool
{
    FastOS_ThreadPool _backendThreadPool;
    std::vector<ThreadImpl*> _threads;
    vespalib::Lock _threadVectorLock;
    Clock& _clock;
    bool _stopping;

public:
    ThreadPoolImpl(Clock&);
    ~ThreadPoolImpl();

    Thread::UP startThread(Runnable&, vespalib::stringref id, uint64_t waitTimeMs,
						   uint64_t maxProcessTime, int ticksBeforeWait) override;
    void visitThreads(ThreadVisitor&) const override;

    void registerThread(ThreadImpl&);
    void unregisterThread(ThreadImpl&);
    FastOS_ThreadPool& getThreadPool() { return _backendThreadPool; }
    Clock& getClock() { return _clock; }
};

}
