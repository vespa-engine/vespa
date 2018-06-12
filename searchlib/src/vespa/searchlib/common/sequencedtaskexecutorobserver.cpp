// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutorobserver.h"

namespace search {

SequencedTaskExecutorObserver::SequencedTaskExecutorObserver(ISequencedTaskExecutor &executor)
    : _executor(executor),
      _executeCnt(0u),
      _syncCnt(0u),
      _executeHistory(),
      _mutex()
{
}

SequencedTaskExecutorObserver::~SequencedTaskExecutorObserver() = default;

ISequencedTaskExecutor::ExecutorId
SequencedTaskExecutorObserver::getExecutorId(uint64_t componentId)
{
    return _executor.getExecutorId(componentId);
}

void
SequencedTaskExecutorObserver::executeTask(ExecutorId id, vespalib::Executor::Task::UP task)
{
    ++_executeCnt;
    {
        std::lock_guard<std::mutex> guard(_mutex);
        _executeHistory.emplace_back(id.getId());
    }
    _executor.executeTask(id, std::move(task));
}

void
SequencedTaskExecutorObserver::sync()
{
    ++_syncCnt;
    _executor.sync();
}

std::vector<uint32_t>
SequencedTaskExecutorObserver::getExecuteHistory()
{
    std::lock_guard<std::mutex> guard(_mutex);
    return _executeHistory;
}

} // namespace search
