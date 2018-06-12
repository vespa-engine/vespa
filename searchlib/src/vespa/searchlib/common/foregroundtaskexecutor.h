// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isequencedtaskexecutor.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace vespalib { class ThreadStackExecutorBase; }

namespace search {

/**
 * Class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 *
 * Currently, this is a dummy version that runs everything in the foreground.
 */
class ForegroundTaskExecutor : public ISequencedTaskExecutor
{
    const uint32_t                       _threads;
    vespalib::hash_map<size_t, ExecutorId> _ids;
public:
    using ISequencedTaskExecutor::getExecutorId;

    ForegroundTaskExecutor();
    ForegroundTaskExecutor(uint32_t threads);
    ~ForegroundTaskExecutor() override;

    uint32_t getNumExecutors() const override { return _threads; }
    ExecutorId getExecutorId(uint64_t componentId) override;
    void executeTask(ExecutorId id, vespalib::Executor::Task::UP task) override;
    void sync() override;
};

} // namespace search
