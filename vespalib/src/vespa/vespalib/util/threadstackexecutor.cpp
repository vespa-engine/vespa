// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadstackexecutor.h"

namespace vespalib {

VESPA_THREAD_STACK_TAG(unnamed_nonblocking_executor);

bool
ThreadStackExecutor::acceptNewTask(unique_lock &, std::condition_variable &)
{
    return isRoomForNewTask();
}

void
ThreadStackExecutor::wakeup(unique_lock &, std::condition_variable &)
{
}

ThreadStackExecutor::ThreadStackExecutor(uint32_t threads)
    : ThreadStackExecutor(threads, unnamed_nonblocking_executor)
{ }

ThreadStackExecutor::ThreadStackExecutor(uint32_t threads, uint32_t taskLimit)
    : ThreadStackExecutorBase(taskLimit, unnamed_nonblocking_executor)
{
    start(threads);
}

ThreadStackExecutor::ThreadStackExecutor(uint32_t threads, init_fun_t init_function)
    : ThreadStackExecutor(threads, std::move(init_function), 0xffffffff)
{ }

ThreadStackExecutor::ThreadStackExecutor(uint32_t threads, init_fun_t init_function, uint32_t taskLimit)
    : ThreadStackExecutorBase(taskLimit, std::move(init_function))
{
    start(threads);
}

ThreadStackExecutor::~ThreadStackExecutor()
{
    cleanup();
}

} // namespace vespalib
