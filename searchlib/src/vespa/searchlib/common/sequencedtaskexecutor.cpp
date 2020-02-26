// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutor.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>

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
SequencedTaskExecutor::executeTask(ExecutorId id, vespalib::Executor::Task::UP task)
{
    assert(id.getId() < _executors.size());
    vespalib::ThreadStackExecutorBase &executor(*_executors[id.getId()]);
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
        accumulatedStats += executor->getStats();
    }
    return accumulatedStats;
}

} // namespace search
