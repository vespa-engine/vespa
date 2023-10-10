// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "threadstackexecutorbase.h"

namespace vespalib {

/**
 * An executor service that executes tasks in multiple threads.
 **/
class BlockingThreadStackExecutor : public ThreadStackExecutorBase
{
public:
    bool acceptNewTask(unique_lock & guard, std::condition_variable & cond) override;
    void wakeup(unique_lock & guard, std::condition_variable &) override;

    /**
     * Create a new blocking thread stack executor. The task limit specifies
     * the maximum number of tasks that are currently handled by this
     * executor. Trying to execute more tasks will block.
     *
     * @param threads number of worker threads (concurrent tasks)
     * @param taskLimit upper limit on accepted tasks
     **/
    BlockingThreadStackExecutor(uint32_t threads, uint32_t taskLimit);

    // same as above, but enables you to specify a custom function
    // used to wrap the main loop of all worker threads
    BlockingThreadStackExecutor(uint32_t threads, uint32_t taskLimit, init_fun_t init_function);

    ~BlockingThreadStackExecutor() override;
};

} // namespace vespalib
