// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockingthreadstackexecutor.h"

namespace vespalib {

VESPA_THREAD_STACK_TAG(unnamed_blocking_executor);

bool
BlockingThreadStackExecutor::acceptNewTask(unique_lock & guard, std::condition_variable & cond)
{
    while (!closed() && !isRoomForNewTask() && !owns_this_thread()) {
        cond.wait(guard);
    }
    return (!closed());
}

void
BlockingThreadStackExecutor::wakeup(unique_lock &, std::condition_variable & cond)
{
    cond.notify_all();
}

BlockingThreadStackExecutor::BlockingThreadStackExecutor(uint32_t threads, uint32_t taskLimit)
    : ThreadStackExecutorBase(taskLimit, unnamed_blocking_executor)
{
    start(threads);
}

BlockingThreadStackExecutor::BlockingThreadStackExecutor(uint32_t threads, uint32_t taskLimit,
                                                         init_fun_t init_function)
    : ThreadStackExecutorBase(taskLimit, std::move(init_function))
{
    start(threads);
}

BlockingThreadStackExecutor::~BlockingThreadStackExecutor()
{
    cleanup();
}

} // namespace vespalib
