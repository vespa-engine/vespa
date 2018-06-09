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
    : ISequencedTaskExecutor(threads)
{
}

ForegroundTaskExecutor::~ForegroundTaskExecutor() = default;

void
ForegroundTaskExecutor::executeTask(uint32_t executorId, vespalib::Executor::Task::UP task)
{
    assert(executorId < getNumExecutors());
    task->run();
}


void
ForegroundTaskExecutor::sync()
{
}

} // namespace search
