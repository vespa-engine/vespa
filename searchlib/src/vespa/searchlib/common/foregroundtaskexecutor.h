// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isequencedtaskexecutor.h"

namespace vespalib
{

class ThreadStackExecutorBase;

}

namespace search
{

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

    ~ForegroundTaskExecutor();

    virtual uint32_t getExecutorId(uint64_t componentId) override;

    virtual void executeTask(uint32_t executorId, vespalib::Executor::Task::UP task) override;

    virtual void sync() override;
};

} // namespace search
