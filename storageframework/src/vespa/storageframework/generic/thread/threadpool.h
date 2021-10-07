// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    std::atomic<vespalib::duration> _maxProcessTime;
    /**
     * Time this thread will wait in a non-interrupted wait cycle.
     * Used in cases where a wait cycle is registered. As long as no other
     * time consuming stuff is done in a wait cycle, you can just use the
     * wait time here. The deadlock detector should add a configurable
     * global time period before flagging deadlock anyways.
     */
    std::atomic<vespalib::duration> _waitTime;
    /**
     * Number of ticks to be done before a wait.
     */
    std::atomic_uint _ticksBeforeWait;

 public:
    ThreadProperties(vespalib::duration waitTime,
                     vespalib::duration maxProcessTime,
                     int ticksBeforeWait);

    void setMaxProcessTime(vespalib::duration);
    void setWaitTime(vespalib::duration);
    void setTicksBeforeWait(int);

    vespalib::duration getMaxProcessTime() const;
    vespalib::duration getWaitTime() const;
    int getTicksBeforeWait() const;

    vespalib::duration getMaxCycleTime() const {
      return std::max(_maxProcessTime.load(std::memory_order_relaxed),
                      _waitTime.load(std::memory_order_relaxed));
    }
};

/** Data kept on each thread due to the registerTick functinality. */
struct ThreadTickData {
    CycleType _lastTickType;
    vespalib::steady_time _lastTick;
    vespalib::duration _maxProcessingTimeSeen;
    vespalib::duration _maxWaitTimeSeen;
};

/** Interface used to access data for the existing threads. */
struct ThreadVisitor {
    virtual ~ThreadVisitor() = default;
    virtual void visitThread(const vespalib::string& id,
                             const ThreadProperties&,
                             const ThreadTickData&) = 0;
};

struct ThreadPool {
    virtual ~ThreadPool() = default;

    virtual Thread::UP startThread(Runnable&,
                                   vespalib::stringref id,
                                   vespalib::duration waitTime,
                                   vespalib::duration maxProcessTime,
                                   int ticksBeforeWait) = 0;

    virtual void visitThreads(ThreadVisitor&) const = 0;
};

}
