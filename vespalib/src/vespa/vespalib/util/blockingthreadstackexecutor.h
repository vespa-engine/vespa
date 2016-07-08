// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2010 Yahoo

#pragma once

#include "threadstackexecutorbase.h"

namespace vespalib {

/**
 * An executor service that executes tasks in multiple threads.
 **/
class BlockingThreadStackExecutor : public ThreadStackExecutorBase
{
private:
    virtual bool acceptNewTask(MonitorGuard & monitor);
    virtual void wakeup(MonitorGuard & monitor);
public:
    /**
     * Create a new blocking thread stack executor. The task limit specifies
     * the maximum number of tasks that are currently handled by this
     * executor. Trying to execute more tasks will block.
     *
     * @param threads number of worker threads (concurrent tasks)
     * @param stackSize stack size per worker thread
     * @param taskLimit upper limit on accepted tasks
     **/
    BlockingThreadStackExecutor(uint32_t threads, uint32_t stackSize, uint32_t taskLimit);
    ~BlockingThreadStackExecutor();

    /**
     * Sets a new upper limit for accepted number of tasks.
     */
    void setTaskLimit(uint32_t taskLimit);
};

} // namespace vespalib

