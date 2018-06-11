// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isequencedtaskexecutor.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>

namespace vespalib {
    class ExecutorStats;
    class BlockingThreadStackExecutor;
}

namespace search {

/**
 * Class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 */
class SequencedTaskExecutor : public ISequencedTaskExecutor
{
    using Stats = vespalib::ExecutorStats;
    std::vector<std::shared_ptr<vespalib::BlockingThreadStackExecutor>> _executors;
    vespalib::hash_map<size_t, size_t> _ids;
public:
    using ISequencedTaskExecutor::getExecutorId;

    SequencedTaskExecutor(uint32_t threads, uint32_t taskLimit = 1000);
    ~SequencedTaskExecutor();

    void setTaskLimit(uint32_t taskLimit);
    uint32_t getNumExecutors() const override { return _executors.size(); }
    uint32_t getExecutorId(uint64_t componentId) override;
    void executeTask(uint32_t executorId, vespalib::Executor::Task::UP task) override;
    void sync() override;
    Stats getStats();
};

} // namespace search
