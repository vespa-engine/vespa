// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutor.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>

using vespalib::BlockingThreadStackExecutor;
using vespalib::SingleExecutor;

namespace search {

namespace {

constexpr uint32_t stackSize = 128 * 1024;

}


std::unique_ptr<ISequencedTaskExecutor>
SequencedTaskExecutor::create(uint32_t threads, uint32_t taskLimit, OptimizeFor optimize)
{
    auto executors = std::make_unique<std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>>>();
    executors->reserve(threads);
    for (uint32_t id = 0; id < threads; ++id) {
        if (optimize == OptimizeFor::THROUGHPUT) {
            executors->push_back(std::make_unique<SingleExecutor>(taskLimit));
        } else {
            executors->push_back(std::make_unique<BlockingThreadStackExecutor>(1, stackSize, taskLimit));
        }
    }
    return std::unique_ptr<ISequencedTaskExecutor>(new SequencedTaskExecutor(std::move(executors)));
}

SequencedTaskExecutor::~SequencedTaskExecutor()
{
    sync();
}

SequencedTaskExecutor::SequencedTaskExecutor(std::unique_ptr<std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>>> executors)
    : ISequencedTaskExecutor(executors->size()),
      _executors(std::move(executors))
{
}

void
SequencedTaskExecutor::setTaskLimit(uint32_t taskLimit)
{
    for (const auto &executor : *_executors) {
        executor->setTaskLimit(taskLimit);
    }
}

void
SequencedTaskExecutor::executeTask(ExecutorId id, vespalib::Executor::Task::UP task)
{
    assert(id.getId() < _executors->size());
    auto rejectedTask = (*_executors)[id.getId()]->execute(std::move(task));
    assert(!rejectedTask);
}

void
SequencedTaskExecutor::sync()
{
    for (auto &executor : *_executors) {
        executor->sync();
    }
}

SequencedTaskExecutor::Stats
SequencedTaskExecutor::getStats()
{
    Stats accumulatedStats;
    for (auto &executor :* _executors) {
        accumulatedStats += executor->getStats();
    }
    return accumulatedStats;
}

} // namespace search
