// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockingthreadstackexecutor.h"

namespace vespalib {

VESPA_THREAD_STACK_TAG(unnamed_blocking_executor);

bool
BlockingThreadStackExecutor::acceptNewTask(MonitorGuard & guard)
{
    while (!closed() && !isRoomForNewTask() && !owns_this_thread()) {
        guard.wait();
    }
    return (!closed());
}

void
BlockingThreadStackExecutor::wakeup(MonitorGuard & monitor)
{
    monitor.broadcast();
}

BlockingThreadStackExecutor::BlockingThreadStackExecutor(uint32_t threads, uint32_t stackSize, uint32_t taskLimit)
    : ThreadStackExecutorBase(stackSize, taskLimit, unnamed_blocking_executor)
{
    start(threads);
}

BlockingThreadStackExecutor::BlockingThreadStackExecutor(uint32_t threads, uint32_t stackSize, uint32_t taskLimit,
                                                         init_fun_t init_function)
    : ThreadStackExecutorBase(stackSize, taskLimit, std::move(init_function))
{
    start(threads);
}

BlockingThreadStackExecutor::~BlockingThreadStackExecutor()
{
    cleanup();
}

} // namespace vespalib
