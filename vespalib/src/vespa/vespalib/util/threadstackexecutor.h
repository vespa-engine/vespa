// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    /**
     * Create a new thread stack executor. The task limit specifies
     * the maximum number of tasks that are currently handled by this
     * executor. Both the number of threads and the task limit must be
     * greater than 0.
     *
     * @param threads number of worker threads (concurrent tasks)
     * @param taskLimit upper limit on accepted tasks
     **/
    ThreadStackExecutor(uint32_t threads, uint32_t taskLimit);
    ThreadStackExecutor(uint32_t threads);

    // same as above, but enables you to specify a custom function
    // used to wrap the main loop of all worker threads
    ThreadStackExecutor(uint32_t threads, init_fun_t init_function, uint32_t taskLimit);
    ThreadStackExecutor(uint32_t threads, init_fun_t init_function);

    ~ThreadStackExecutor() override;
};

} // namespace vespalib

