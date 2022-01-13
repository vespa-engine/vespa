// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "isequencedtaskexecutor.h"
#include <vespa/vespalib/stllike/hash_fun.h>

namespace vespalib {

ISequencedTaskExecutor::ISequencedTaskExecutor(uint32_t numExecutors)
    : _numExecutors(numExecutors)
{
}

ISequencedTaskExecutor::~ISequencedTaskExecutor() = default;

void
ISequencedTaskExecutor::executeTasks(TaskList tasks) {
    for (auto & task : tasks) {
        executeTask(task.first, std::move(task.second));
    }
}

ISequencedTaskExecutor::ExecutorId
ISequencedTaskExecutor::getExecutorIdFromName(stringref componentId) const {
    hash<stringref> hashfun;
    return getExecutorId(hashfun(componentId));
}

ISequencedTaskExecutor::ExecutorId
ISequencedTaskExecutor::get_alternate_executor_id(ExecutorId id, uint32_t bias) const
{
    if ((bias % _numExecutors) == 0) {
        bias = 1;
    }
    return ExecutorId((id.getId() + bias) % _numExecutors);
}

}
