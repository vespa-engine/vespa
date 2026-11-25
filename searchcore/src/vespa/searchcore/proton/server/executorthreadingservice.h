// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_thread_service.h"
#include "threading_service_config.h"

#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/invokeservice.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <atomic>

namespace proton {

class ExecutorThreadingServiceStats;

/**
 * Implementation of IThreadingService using 2 underlying thread stack executors
 * with 1 thread each.
 */
class ExecutorThreadingService : public searchcorespi::index::IThreadingService {
private:
    using Registration = std::unique_ptr<vespalib::IDestructorCallback>;
    vespalib::Executor&                               _sharedExecutor;
    FNET_Transport&                                   _transport;
    vespalib::ThreadStackExecutor                     _main_executor;
    std::atomic<uint32_t>                             _main_task_limit;
    std::unique_ptr<vespalib::SyncableThreadExecutor> _indexExecutor;
    std::unique_ptr<vespalib::SyncableThreadExecutor> _summaryExecutor;
    SyncableExecutorThreadService                     _main_service;
    ExecutorThreadService                             _indexService;
    vespalib::ISequencedTaskExecutor&                 _field_writer;
    std::vector<Registration>                         _invokeRegistrations;

public:
    using OptimizeFor = vespalib::Executor::OptimizeFor;
    /**
     * Convenience constructor used in unit tests.
     */
    ExecutorThreadingService(vespalib::Executor& sharedExecutor, FNET_Transport& transport,
                             vespalib::ISequencedTaskExecutor& field_writer);

    ExecutorThreadingService(vespalib::Executor& sharedExecutor, FNET_Transport& transport,
                             vespalib::ISequencedTaskExecutor& field_writer, vespalib::InvokeService* invokeService,
                             const ThreadingServiceConfig& cfg);
    ~ExecutorThreadingService() override;

    void blocking_main_execute(vespalib::Executor::Task::UP task) noexcept override;

    void shutdown();

    [[nodiscard]] uint32_t main_task_limit() const { return _main_task_limit.load(std::memory_order_relaxed); }
    void set_task_limits(uint32_t main_task_limit, uint32_t field_task_limit, uint32_t summary_task_limit);

    [[nodiscard]] searchcorespi::index::ISyncableThreadService& main() noexcept override { return _main_service; }
    [[nodiscard]] searchcorespi::index::IThreadService& index() noexcept override { return _indexService; }

    [[nodiscard]] vespalib::ThreadExecutor& summary() noexcept override { return *_summaryExecutor; }
    [[nodiscard]] vespalib::Executor& shared() noexcept override { return _sharedExecutor; }

    [[nodiscard]] vespalib::ISequencedTaskExecutor& field_writer() noexcept override;
    [[nodiscard]] FNET_Transport& transport() noexcept override { return _transport; }
    [[nodiscard]] ExecutorThreadingServiceStats getStats();
};

} // namespace proton
