// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isequencedtaskexecutor.h"
#include <atomic>

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
public:
    using ISequencedTaskExecutor::getExecutorId;

    ForegroundTaskExecutor();
    ForegroundTaskExecutor(uint32_t threads);
    ~ForegroundTaskExecutor() override;

    void executeTask(ExecutorId id, vespalib::Executor::Task::UP task) override;
    void sync() override;

    void setTaskLimit(uint32_t taskLimit) override;

    vespalib::ExecutorStats getStats() override;
private:
    std::atomic<uint64_t> _accepted;
};

} // namespace search
