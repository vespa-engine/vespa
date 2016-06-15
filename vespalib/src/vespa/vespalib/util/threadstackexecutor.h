// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2010 Yahoo

#pragma once

#include "threadstackexecutorbase.h"

namespace vespalib {

/**
 * An executor service that executes tasks in multiple threads.
 **/
class ThreadStackExecutor : public ThreadStackExecutorBase
{
public:
    /**
     * This will tell if a task will be accepted or not.
     * An implementation might decide to block.
     */
    virtual bool acceptNewTask(MonitorGuard & monitor);

    /**
     * If blocking implementation, this might wake up any waiters.
     *
     * @param monitor to use for signaling.
     */
    virtual void wakeup(MonitorGuard & monitor);

public:
    /**
     * Create a new thread stack executor. The task limit specifies
     * the maximum number of tasks that are currently handled by this
     * executor. Both the number of threads and the task limit must be
     * greater than 0.
     *
     * @param threads number of worker threads (concurrent tasks)
     * @param stackSize stack size per worker thread
     * @param taskLimit upper limit on accepted tasks
     **/
    ThreadStackExecutor(uint32_t threads, uint32_t stackSize,
                        uint32_t taskLimit = 0xffffffff);

    /**
     * Will invoke cleanup.
     **/
    ~ThreadStackExecutor();
};

} // namespace vespalib

