// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutor.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::BlockingThreadStackExecutor;

namespace search {

namespace {

constexpr uint32_t stackSize = 128 * 1024;

}

SequencedTaskExecutor::SequencedTaskExecutor(uint32_t threads, uint32_t taskLimit)
    : ISequencedTaskExecutor(threads),
      _executors()
{
    for (uint32_t id = 0; id < threads; ++id) {
        auto executor = std::make_unique<BlockingThreadStackExecutor>(1, stackSize, taskLimit);
        _executors.push_back(std::move(executor));
    }
}

SequencedTaskExecutor::~SequencedTaskExecutor()
{
    sync();
}

void
SequencedTaskExecutor::setTaskLimit(uint32_t taskLimit)
{
    for (const auto &executor : _executors) {
        executor->setTaskLimit(taskLimit);
    }
}


void
SequencedTaskExecutor::executeTask(uint32_t executorId, vespalib::Executor::Task::UP task)
{
    assert(executorId < _executors.size());
    vespalib::ThreadStackExecutorBase &executor(*_executors[executorId]);
    auto rejectedTask = executor.execute(std::move(task));
    assert(!rejectedTask);
}


void
SequencedTaskExecutor::sync()
{
    for (auto &executor : _executors) {
        executor->sync();
    }
}

SequencedTaskExecutor::Stats
SequencedTaskExecutor::getStats()
{
    Stats accumulatedStats;
    for (auto &executor : _executors) {
        Stats stats = executor->getStats();
        accumulatedStats.maxPendingTasks += stats.maxPendingTasks;
        accumulatedStats.acceptedTasks += stats.acceptedTasks;
        accumulatedStats.rejectedTasks += stats.rejectedTasks;
    }
    return accumulatedStats;
}

} // namespace search
