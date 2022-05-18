// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "foregroundtaskexecutor.h"
#include <cassert>

namespace vespalib {

ForegroundTaskExecutor::ForegroundTaskExecutor()
    : ForegroundTaskExecutor(1)
{
}

ForegroundTaskExecutor::ForegroundTaskExecutor(uint32_t threads)
    : ISequencedTaskExecutor(threads),
      _accepted(0)
{
}

ForegroundTaskExecutor::~ForegroundTaskExecutor() = default;

void
ForegroundTaskExecutor::executeTask(ExecutorId id, Executor::Task::UP task)
{
    assert(id.getId() < getNumExecutors());
    task->run();
    _accepted++;
}

void
ForegroundTaskExecutor::sync_all()
{
}

void ForegroundTaskExecutor::setTaskLimit(uint32_t) {

}

ExecutorStats ForegroundTaskExecutor::getStats() {
    return ExecutorStats(ExecutorStats::QueueSizeT(0) , _accepted.load(std::memory_order_relaxed), 0, 0);
}

ISequencedTaskExecutor::ExecutorId
ForegroundTaskExecutor::getExecutorId(uint64_t componentId) const {
    return ExecutorId(componentId%getNumExecutors());
}

}
