// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "foregroundtaskexecutor.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::ThreadStackExecutor;

namespace search {

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
ForegroundTaskExecutor::executeTask(ExecutorId id, vespalib::Executor::Task::UP task)
{
    assert(id.getId() < getNumExecutors());
    task->run();
    _accepted++;
}

void
ForegroundTaskExecutor::sync()
{
}

void ForegroundTaskExecutor::setTaskLimit(uint32_t) {

}

vespalib::ExecutorStats ForegroundTaskExecutor::getStats() {
    return vespalib::ExecutorStats(0, _accepted.load(std::memory_order_relaxed), 0);
}

} // namespace search
