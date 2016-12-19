// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadstackexecutor.h"

namespace vespalib {

bool
ThreadStackExecutor::acceptNewTask(MonitorGuard &)
{
    return isRoomForNewTask();
}

void
ThreadStackExecutor::wakeup(MonitorGuard &)
{
}

ThreadStackExecutor::ThreadStackExecutor(uint32_t threads, uint32_t stackSize, uint32_t taskLimit) :
    ThreadStackExecutorBase(stackSize, taskLimit)
{
    start(threads);
}

ThreadStackExecutor::~ThreadStackExecutor()
{
    cleanup();
}

} // namespace vespalib
