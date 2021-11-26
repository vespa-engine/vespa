// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isequencedtaskexecutor.h"
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/runnable.h>
#include <optional>

namespace vespalib {

class ThreadExecutor;
class SyncableThreadExecutor;

/**
 * Class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 */
class SequencedTaskExecutor final : public ISequencedTaskExecutor
{
public:
    using ISequencedTaskExecutor::getExecutorId;
    using OptimizeFor = vespalib::Executor::OptimizeFor;

    ~SequencedTaskExecutor() override;

    void setTaskLimit(uint32_t taskLimit) override;
    void executeTask(ExecutorId id, vespalib::Executor::Task::UP task) override;
    ExecutorId getExecutorId(uint64_t componentId) const override;
    void sync_all() override;
    ExecutorStats getStats() override;
    void wakeup() override;

    /*
     * Note that if you choose Optimize::THROUGHPUT, you must ensure only a single producer, or synchronize on the outside.
     *
     */
    static std::unique_ptr<ISequencedTaskExecutor>
    create(vespalib::Runnable::init_fun_t, uint32_t threads, uint32_t taskLimit = 1000,
           OptimizeFor optimize = OptimizeFor::LATENCY, uint32_t kindOfWatermark = 0, duration reactionTime = 10ms);
    /**
     * For testing only
     */
    uint32_t getComponentHashSize() const { return _component2IdImperfect.size(); }
    uint32_t getComponentEffectiveHashSize() const { return _nextId; }
    const vespalib::ThreadExecutor* first_executor() const;

private:
    explicit SequencedTaskExecutor(std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>> executor);
    std::optional<ExecutorId> getExecutorIdPerfect(uint64_t componentId) const;
    ExecutorId getExecutorIdImPerfect(uint64_t componentId) const;

    std::vector<std::unique_ptr<vespalib::SyncableThreadExecutor>> _executors;
    using PerfectKeyT = uint16_t;
    const bool                             _lazyExecutors;
    mutable std::unique_ptr<PerfectKeyT[]> _component2IdPerfect;
    mutable std::vector<uint8_t>           _component2IdImperfect;
    mutable std::mutex                     _mutex;
    mutable uint32_t                       _nextId;

};

} // namespace search
