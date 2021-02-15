// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutor.h"
#include "adaptive_sequenced_executor.h"
#include "singleexecutor.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/stllike/hashtable.h>
#include <cassert>

namespace vespalib {

namespace {

constexpr uint32_t stackSize = 128_Ki;
constexpr uint8_t MAGIC = 255;

bool
isLazy(const std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>> & executors) {
    for (const auto &executor : executors) {
        if (dynamic_cast<const vespalib::SingleExecutor *>(executor.get()) == nullptr) {
            return false;
        }
    }
    return true;
}

}

std::unique_ptr<ISequencedTaskExecutor>
SequencedTaskExecutor::create(vespalib::Runnable::init_fun_t func, uint32_t threads, uint32_t taskLimit,
                              OptimizeFor optimize, uint32_t kindOfWatermark, duration reactionTime)
{
    if (optimize == OptimizeFor::ADAPTIVE) {
        size_t num_strands = std::min(taskLimit, threads*32);
        return std::make_unique<AdaptiveSequencedExecutor>(num_strands, threads, kindOfWatermark, taskLimit);
    } else {
        auto executors = std::make_unique<std::vector<std::unique_ptr<SyncableThreadExecutor>>>();
        executors->reserve(threads);
        for (uint32_t id = 0; id < threads; ++id) {
            if (optimize == OptimizeFor::THROUGHPUT) {
                uint32_t watermark = kindOfWatermark == 0 ? taskLimit / 10 : kindOfWatermark;
                executors->push_back(std::make_unique<SingleExecutor>(func, taskLimit, watermark, reactionTime));
            } else {
                executors->push_back(std::make_unique<BlockingThreadStackExecutor>(1, stackSize, taskLimit, func));
            }
        }
        return std::unique_ptr<ISequencedTaskExecutor>(new SequencedTaskExecutor(std::move(executors)));
    }
}

SequencedTaskExecutor::~SequencedTaskExecutor()
{
    sync();
}

SequencedTaskExecutor::SequencedTaskExecutor(std::unique_ptr<std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>>> executors)
    : ISequencedTaskExecutor(executors->size()),
      _executors(std::move(executors)),
      _lazyExecutors(isLazy(*_executors)),
      _component2Id(vespalib::hashtable_base::getModuloStl(getNumExecutors()*8), MAGIC),
      _mutex(),
      _nextId(0)
{
    assert(getNumExecutors() < 256);
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
SequencedTaskExecutor::sync() {
    wakeup();
    for (auto &executor : *_executors) {
        executor->sync();
    }
}

void
SequencedTaskExecutor::wakeup() {
    if (_lazyExecutors) {
        for (auto &executor : *_executors) {
            //Enforce parallel wakeup of napping executors.
            executor->wakeup();
        }
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

ISequencedTaskExecutor::ExecutorId
SequencedTaskExecutor::getExecutorId(uint64_t componentId) const {
    uint32_t shrunkId = componentId % _component2Id.size();
    uint8_t executorId = _component2Id[shrunkId];
    if (executorId == MAGIC) {
        std::lock_guard guard(_mutex);
        if (_component2Id[shrunkId] == MAGIC) {
            _component2Id[shrunkId] = _nextId % getNumExecutors();
            _nextId++;
        }
        executorId = _component2Id[shrunkId];
    }
    return ExecutorId(executorId);
}

const vespalib::SyncableThreadExecutor*
SequencedTaskExecutor::first_executor() const
{
    if (_executors->empty()) {
        return nullptr;
    }
    return _executors->front().get();
}

} // namespace search
