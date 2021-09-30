// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/threadexecutor.h>

namespace proton {

namespace internal { struct ThreadId; }
/**
 * Implementation of IThreadService using an underlying thread stack executor
 * with a single thread.
 */
class ExecutorThreadService : public searchcorespi::index::IThreadService
{
private:
    vespalib::SyncableThreadExecutor &_executor;
    std::unique_ptr<internal::ThreadId>   _threadId;

public:
    ExecutorThreadService(vespalib::SyncableThreadExecutor &executor);
    ~ExecutorThreadService();

    Stats getStats() override;

    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) override {
        return _executor.execute(std::move(task));
    }
    void run(vespalib::Runnable &runnable) override;
    vespalib::Syncable &sync() override {
        _executor.sync();
        return *this;
    }
    ExecutorThreadService & shutdown() override {
        _executor.shutdown();
        return *this;
    }
    bool isCurrentThread() const override;
    size_t getNumThreads() const override { return _executor.getNumThreads(); }

    void setTaskLimit(uint32_t taskLimit) override;
    uint32_t getTaskLimit() const override;
    void wakeup() override;
};

} // namespace proton


