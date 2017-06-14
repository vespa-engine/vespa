// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::ThreadPool
 * \ingroup thread
 *
 * \brief A threadpool implementation usable by storage components.
 *
 * Using this threadpool interface, we can use a threadpool without depending
 * on the actual implementation. Also, as information is provided of the
 * threads, monitoring tools, like the deadlock detector can extract information
 * about the threads.
 */
#pragma once

#include <atomic>
#include <vespa/storageframework/generic/thread/runnable.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/clock/time.h>
#include <vector>

namespace storage::framework {

/**
 * Each thread may have different properties, as to how long they wait between
 * ticks and how long they're supposed to use processing between ticks. To be
 * able to specify this per thread, a set of properties can be set by each
 * thread.
 */
class ThreadProperties {
 private:
    /**
     * Time this thread should maximum use to process before a tick is
     * registered. (Including wait time if wait time is not set)
     */
    std::atomic_uint_least64_t _maxProcessTimeMs;
    /**
     * Time this thread will wait in a non-interrupted wait cycle.
     * Used in cases where a wait cycle is registered. As long as no other
     * time consuming stuff is done in a wait cycle, you can just use the
     * wait time here. The deadlock detector should add a configurable
     * global time period before flagging deadlock anyways.
     */
     std::atomic_uint_least64_t _waitTimeMs;
    /**
     * Number of ticks to be done before a wait.
     */
    std::atomic_uint _ticksBeforeWait;

 public:
    ThreadProperties(uint64_t waitTimeMs,
                     uint64_t maxProcessTimeMs,
                     int ticksBeforeWait);

    void setMaxProcessTime(uint64_t);
    void setWaitTime(uint64_t);
    void setTicksBeforeWait(int);

    uint64_t getMaxProcessTime() const;
    uint64_t getWaitTime() const;
    int getTicksBeforeWait() const;

    uint64_t getMaxCycleTime() const {
      return std::max(_maxProcessTimeMs.load(std::memory_order_relaxed),
                      _waitTimeMs.load(std::memory_order_relaxed));
    }
};

/** Data kept on each thread due to the registerTick functinality. */
struct ThreadTickData {
    CycleType _lastTickType;
    uint64_t _lastTickMs;
    uint64_t _maxProcessingTimeSeenMs;
    uint64_t _maxWaitTimeSeenMs;
};

/** Interface used to access data for the existing threads. */
struct ThreadVisitor {
    virtual ~ThreadVisitor() {}
    virtual void visitThread(const vespalib::string& id,
                             const ThreadProperties&,
                             const ThreadTickData&) = 0;
};

struct ThreadPool {
    virtual ~ThreadPool() {}

    virtual Thread::UP startThread(Runnable&,
                                   vespalib::stringref id,
                                   uint64_t waitTimeMs,
                                   uint64_t maxProcessTime,
                                   int ticksBeforeWait) = 0;

    virtual void visitThreads(ThreadVisitor&) const = 0;
};

}
