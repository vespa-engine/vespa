// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This file contains a utility function to handle threads doing a lot of
 * single ticks. It brings the following functionality:
 *
 *    - Give application setting up the threads a way to synchronize all the
 *      threads so it can perform some operation while no thread is ticking.
 *    - Give multiple threads a way to use common lock for critical region, such
 *      that you can divide responsible between multiple threads, and still have
 *      a way to notify and wait for all.
 *    - Automatically implement registration in deadlock handler, and updating
 *      tick times there.
 *    - Give a thread specific context to tick functions, such that one class
 *      instance can be used for all threads.
 *    - Hide thread functionality for starting, stopping and running.
 *    - Minimizes locking by using a single lock that is taken only once per
 *      tick loop.
 */
#pragma once

#include <memory>
#include <vespa/storageframework/generic/clock/time.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/sync.h>

namespace storage::framework {

struct ThreadPool;
typedef uint32_t ThreadIndex;

/**
 * \brief Information returned from tick functions to indicate whether thread
 *        should throttle a bit or not.
 */
class ThreadWaitInfo {
    bool _waitWanted;
    ThreadWaitInfo(bool waitBeforeNextTick) : _waitWanted(waitBeforeNextTick) {}

public:
    static ThreadWaitInfo MORE_WORK_ENQUEUED;
    static ThreadWaitInfo NO_MORE_CRITICAL_WORK_KNOWN;

    void merge(const ThreadWaitInfo& other);
    bool waitWanted() { return _waitWanted; }
};

/**
 * \brief Simple superclass to implement for ticking threads.
 */
struct TickingThread {
    virtual ~TickingThread() {}

    virtual ThreadWaitInfo doCriticalTick(ThreadIndex) = 0;
    virtual ThreadWaitInfo doNonCriticalTick(ThreadIndex) = 0;
    virtual void newThreadCreated(ThreadIndex) {}
};

/** \brief Delete to allow threads to tick again. */
struct TickingLockGuard {
    struct Impl {
        virtual ~Impl() {}
        virtual void broadcast() = 0;
    };
    TickingLockGuard(std::unique_ptr<Impl> impl) : _impl(std::move(impl)) {}
    void broadcast() { _impl->broadcast(); }
private:
    std::unique_ptr<Impl> _impl;
};

struct ThreadLock {
    virtual ~ThreadLock() { }
    virtual TickingLockGuard freezeAllTicks() = 0;
    virtual TickingLockGuard freezeCriticalTicks() = 0;
};

/**
 * \brief Thread pool set up by the application to control the threads.
 */
struct TickingThreadPool : public ThreadLock {
    typedef std::unique_ptr<TickingThreadPool> UP;

    static TickingThreadPool::UP createDefault(
            vespalib::stringref name,
            MilliSecTime waitTime = MilliSecTime(5),
            int ticksBeforeWait = 1,
            MilliSecTime maxProcessTime = SecondTime(5).getMillis());

    virtual void updateParametersAllThreads(
            MilliSecTime waitTime,
            MilliSecTime maxProcessTime,
            int ticksBeforeWait) = 0;


    virtual ~TickingThreadPool() {}

    /** All threads must be added before starting the threads. */
    virtual void addThread(TickingThread& ticker) = 0;
    /** Start all the threads added. */
    virtual void start(ThreadPool& pool) = 0;
    virtual void stop() = 0;
    virtual vespalib::string getStatus() = 0;
};

}
