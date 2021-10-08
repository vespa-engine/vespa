// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "threadstackexecutorbase.h"

namespace vespalib {

/**
 * An executor service that executes tasks in multiple threads.
 **/
class ThreadStackExecutor : public ThreadStackExecutorBase
{
public:
    bool acceptNewTask(unique_lock &, std::condition_variable &) override;
    void wakeup(unique_lock &, std::condition_variable &) override;

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

    // same as above, but enables you to specify a custom function
    // used to wrap the main loop of all worker threads
    ThreadStackExecutor(uint32_t threads, uint32_t stackSize,
                        init_fun_t init_function,
                        uint32_t taskLimit = 0xffffffff);

    /**
     * Will invoke cleanup.
     **/
    ~ThreadStackExecutor() override;
};

} // namespace vespalib

