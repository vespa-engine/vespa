// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Runnable
 * \ingroup thread
 *
 * \brief Minimal API for something that can be run.
 *
 * Minimum API to implement to be able to be run by a thread.
 */
#pragma once

#include <vespa/vespalib/util/time.h>

namespace storage::framework {

/**
 * A cycle type can be given when registering ticks. This is useful for
 * monitoring, to see the difference between cycles that is just waiting and
 * cycles that are processing. If this information is known, the monitoring
 * tools can see that the longest process cycle have been 5 ms, even though
 * the thread is waiting for 1000 ms when it is idle.
 */
enum CycleType { UNKNOWN_CYCLE, WAIT_CYCLE, PROCESS_CYCLE };

struct ThreadHandle {
    virtual ~ThreadHandle() = default;

    /** Check whether thread have been interrupted or not. */
    [[nodiscard]] virtual bool interrupted() const = 0;

    /**
     * Register a tick. Useful such that a deadlock detector can detect that
     * threads are actually doing something. If cycle types are specified,
     * deadlock detector can specifically know what thread has been doing and
     * used appropriate max limit. On unknown cycles, less information is
     * available, and deadlock detector will use sum of wait and process time.
     *
     * The cycle type specified is for the cycle that just passed.
     *
     * @param currentTime Callers can set current time such that backend does
     *                    not need to calculate clock. (Too avoid additional
     *                    clock fetches if client already knows current time)
     */
    virtual void registerTick(CycleType cycleType, vespalib::steady_time time) = 0;
    virtual void registerTick(CycleType cycleType) = 0;

    [[nodiscard]] virtual vespalib::duration getWaitTime() const = 0;

    /**
     * The number of ticks done before wait is called when no more work is
     * reported.
     */
    [[nodiscard]] virtual int getTicksBeforeWait() const = 0;
};

struct Runnable {
    virtual ~Runnable() = default;

    virtual void run(ThreadHandle&) = 0;
};

} // storage::framework
