// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequencedtaskexecutor.h"
#include "adaptive_sequenced_executor.h"
#include "singleexecutor.h"
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/stllike/hashtable.h>
#include <cassert>

namespace vespalib {

namespace {

constexpr uint32_t stackSize = 128_Ki;
constexpr uint8_t MAGIC = 255;
constexpr uint32_t NUM_PERFECT_PER_EXECUTOR = 8;
constexpr uint16_t INVALID_KEY = 0x8000;

bool
isLazy(const std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>> & executors) {
    for (const auto &executor : executors) {
        if (dynamic_cast<const vespalib::SingleExecutor *>(executor.get()) == nullptr) {
            return false;
        }
    }
    return true;
}

ssize_t
find(uint16_t key, const uint16_t values[], size_t numValues) {
    for (size_t i(0); i < numValues; i++) {
        auto value = vespalib::atomic::load_ref_relaxed(values[i]);
        if (key == value) {
            return i;
        }
        if (INVALID_KEY == value) {
            return -1;
        }
    }
    return -1;
}


}

std::unique_ptr<ISequencedTaskExecutor>
SequencedTaskExecutor::create(Runnable::init_fun_t func, uint32_t threads) {
    return create(func, threads, 1000);
}

std::unique_ptr<ISequencedTaskExecutor>
SequencedTaskExecutor::create(Runnable::init_fun_t func, uint32_t threads, uint32_t taskLimit) {
    return create(func, threads, taskLimit, true, OptimizeFor::LATENCY);
}

std::unique_ptr<ISequencedTaskExecutor>
SequencedTaskExecutor::create(Runnable::init_fun_t func, uint32_t threads, uint32_t taskLimit, bool is_task_limit_hard, OptimizeFor optimize) {
    return create(func, threads, taskLimit, is_task_limit_hard, optimize, 0);
}

std::unique_ptr<ISequencedTaskExecutor>
SequencedTaskExecutor::create(Runnable::init_fun_t func, uint32_t threads, uint32_t taskLimit,
                              bool is_task_limit_hard, OptimizeFor optimize, uint32_t kindOfWatermark)
{
    if (optimize == OptimizeFor::ADAPTIVE) {
        size_t num_strands = std::min(taskLimit, threads*32);
        return std::make_unique<AdaptiveSequencedExecutor>(num_strands, threads, kindOfWatermark, taskLimit, is_task_limit_hard);
    } else {
        auto executors = std::vector<std::unique_ptr<SyncableThreadExecutor>>();
        executors.reserve(threads);
        for (uint32_t id = 0; id < threads; ++id) {
            if (optimize == OptimizeFor::THROUGHPUT) {
                uint32_t watermark = (kindOfWatermark == 0) ? taskLimit / 10 : kindOfWatermark;
                executors.push_back(std::make_unique<SingleExecutor>(func, taskLimit, is_task_limit_hard, watermark, 100ms));
            } else {
                if (is_task_limit_hard) {
                    executors.push_back(std::make_unique<BlockingThreadStackExecutor>(1, stackSize, taskLimit, func));
                } else {
                    executors.push_back(std::make_unique<ThreadStackExecutor>(1, stackSize, func));
                }
            }
        }
        return std::unique_ptr<ISequencedTaskExecutor>(new SequencedTaskExecutor(std::move(executors)));
    }
}

SequencedTaskExecutor::~SequencedTaskExecutor()
{
    sync_all();
}

SequencedTaskExecutor::SequencedTaskExecutor(std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>> executors)
    : ISequencedTaskExecutor(executors.size()),
      _executors(std::move(executors)),
      _lazyExecutors(isLazy(_executors)),
      _component2IdPerfect(std::make_unique<PerfectKeyT[]>(getNumExecutors()*NUM_PERFECT_PER_EXECUTOR)),
      _component2IdImperfect(vespalib::hashtable_base::getModuloStl(getNumExecutors()*NUM_PERFECT_PER_EXECUTOR), MAGIC),
      _mutex(),
      _nextId(0)
{
    assert(getNumExecutors() < 256);

    for (size_t i(0); i < getNumExecutors() * NUM_PERFECT_PER_EXECUTOR; i++) {
        _component2IdPerfect[i] = INVALID_KEY;
    }
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
    auto rejectedTask = _executors[id.getId()]->execute(std::move(task));
    assert(!rejectedTask);
}

void
SequencedTaskExecutor::sync_all() {
    wakeup();
    for (auto &executor : _executors) {
        executor->sync();
    }
}

void
SequencedTaskExecutor::wakeup() {
    if (_lazyExecutors) {
        for (auto &executor : _executors) {
            //Enforce parallel wakeup of napping executors.
            executor->wakeup();
        }
    }
}

ExecutorStats
SequencedTaskExecutor::getStats()
{
    ExecutorStats accumulatedStats;
    for (auto &executor : _executors) {
        accumulatedStats.aggregate(executor->getStats());
    }
    return accumulatedStats;
}

ISequencedTaskExecutor::ExecutorId
SequencedTaskExecutor::getExecutorId(uint64_t componentId) const {
    auto id = getExecutorIdPerfect(componentId);
    return id ? id.value() : getExecutorIdImPerfect(componentId);
}

std::optional<ISequencedTaskExecutor::ExecutorId>
SequencedTaskExecutor::getExecutorIdPerfect(uint64_t componentId) const {
    PerfectKeyT key = componentId & 0x7fff;
    ssize_t pos = find(key, _component2IdPerfect.get(), getNumExecutors() * NUM_PERFECT_PER_EXECUTOR);
    if (pos < 0) {
        std::unique_lock guard(_mutex);
        pos = find(key, _component2IdPerfect.get(), getNumExecutors() * NUM_PERFECT_PER_EXECUTOR);
        if (pos < 0) {
            pos = find(INVALID_KEY, _component2IdPerfect.get(), getNumExecutors() * NUM_PERFECT_PER_EXECUTOR);
            if (pos >= 0) {
                vespalib::atomic::store_ref_relaxed(_component2IdPerfect[pos], key);
            } else {
                // There was a race for the last spots
                return std::optional<ISequencedTaskExecutor::ExecutorId>();
            }
        }
    }
    return std::optional<ISequencedTaskExecutor::ExecutorId>(ExecutorId(pos % getNumExecutors()));
}

ISequencedTaskExecutor::ExecutorId
SequencedTaskExecutor::getExecutorIdImPerfect(uint64_t componentId) const {
    uint32_t shrunkId = componentId % _component2IdImperfect.size();
    uint8_t executorId = vespalib::atomic::load_ref_relaxed(_component2IdImperfect[shrunkId]);
    if (executorId == MAGIC) {
        std::lock_guard guard(_mutex);
        if (vespalib::atomic::load_ref_relaxed(_component2IdImperfect[shrunkId]) == MAGIC) {
            vespalib::atomic::store_ref_relaxed(_component2IdImperfect[shrunkId], _nextId % getNumExecutors());
            _nextId++;
        }
        executorId = _component2IdImperfect[shrunkId];
    }
    return ExecutorId(executorId);
}

const vespalib::ThreadExecutor*
SequencedTaskExecutor::first_executor() const
{
    if (_executors.empty()) {
        return nullptr;
    }
    return _executors.front().get();
}

} // namespace search
