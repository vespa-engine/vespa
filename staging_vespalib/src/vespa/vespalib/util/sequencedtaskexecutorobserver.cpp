// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutorobserver.h"

namespace vespalib {

SequencedTaskExecutorObserver::SequencedTaskExecutorObserver(ISequencedTaskExecutor &executor)
    : ISequencedTaskExecutor(executor.getNumExecutors()),
      _executor(executor),
      _executeCnt(0u),
      _syncCnt(0u),
      _executeHistory(),
      _mutex()
{
}

SequencedTaskExecutorObserver::~SequencedTaskExecutorObserver() = default;

void
SequencedTaskExecutorObserver::executeTask(ExecutorId id, Executor::Task::UP task)
{
    ++_executeCnt;
    {
        std::lock_guard<std::mutex> guard(_mutex);
        _executeHistory.emplace_back(id.getId());
    }
    _executor.executeTask(id, std::move(task));
}

void
SequencedTaskExecutorObserver::executeTasks(TaskList tasks)
{
    _executeCnt += tasks.size();
    {
        std::lock_guard<std::mutex> guard(_mutex);
        for (const auto & task : tasks) {
            _executeHistory.emplace_back(task.first.getId());
        }
    }
    _executor.executeTasks(std::move(tasks));
}

void
SequencedTaskExecutorObserver::sync_all()
{
    ++_syncCnt;
    _executor.sync_all();
}

std::vector<uint32_t>
SequencedTaskExecutorObserver::getExecuteHistory()
{
    std::lock_guard<std::mutex> guard(_mutex);
    return _executeHistory;
}

void SequencedTaskExecutorObserver::setTaskLimit(uint32_t taskLimit) {
    _executor.setTaskLimit(taskLimit);
}

ExecutorStats SequencedTaskExecutorObserver::getStats() {
    return _executor.getStats();
}

ISequencedTaskExecutor::ExecutorId
SequencedTaskExecutorObserver::getExecutorId(uint64_t componentId) const {
    return _executor.getExecutorId(componentId);
}

} // namespace search
