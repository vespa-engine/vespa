// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "executor.h"
#include "syncable.h"
#include "executor_stats.h"

namespace vespalib {

class ThreadExecutor : public Executor
{
public:
    /**
     * Get number of threads in the executor pool.
     * @return number of threads in the pool
     */
    virtual size_t getNumThreads() const = 0;

    /**
     * Observe and reset stats for this object.
     * @return stats
     **/
    virtual ExecutorStats getStats() = 0;

    /**
     * Sets a new upper limit for accepted number of tasks.
     */
    virtual void setTaskLimit(uint32_t taskLimit) = 0;

    /**
     * Gets the limit for accepted number of tasks.
     */
    virtual uint32_t getTaskLimit() const = 0;
};

/**
 * Can both execute and sync
 **/
class SyncableThreadExecutor : public ThreadExecutor, public Syncable
{
public:
    virtual SyncableThreadExecutor & shutdown() = 0;
};

} // namespace vespalib

