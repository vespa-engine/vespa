// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "blockingthreadstackexecutor.h"

namespace vespalib {

BlockingThreadStackExecutor::BlockingThreadStackExecutor(uint32_t threads, uint32_t stackSize, uint32_t taskLimit) :
    ThreadStackExecutorBase(stackSize, taskLimit)
{
    start(threads);
}

BlockingThreadStackExecutor::~BlockingThreadStackExecutor()
{
    cleanup();
}

bool
BlockingThreadStackExecutor::acceptNewTask(MonitorGuard & guard)
{
    while (!closed() && !isRoomForNewTask()) {
        guard.wait();
    }
    return (!closed());
}

void
BlockingThreadStackExecutor::wakeup(MonitorGuard & monitor)
{
    monitor.broadcast();
}

void
BlockingThreadStackExecutor::setTaskLimit(uint32_t taskLimit)
{
    internalSetTaskLimit(taskLimit);
}

} // namespace vespalib
