// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_thread_service.h"
#include "threading_service_config.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/invokeservice.h>
#include <atomic>

namespace proton {

class ExecutorThreadingServiceStats;

/**
 * Implementation of IThreadingService using 2 underlying thread stack executors
 * with 1 thread each.
 */
class ExecutorThreadingService : public searchcorespi::index::IThreadingService
{
private:
    using Registration = std::unique_ptr<vespalib::IDestructorCallback>;
    vespalib::ThreadExecutor                           & _sharedExecutor;
    vespalib::ThreadStackExecutor                        _masterExecutor;
    ThreadingServiceConfig::SharedFieldWriterExecutor    _shared_field_writer;
    std::atomic<uint32_t>                                _master_task_limit;
    std::unique_ptr<vespalib::SyncableThreadExecutor>    _indexExecutor;
    std::unique_ptr<vespalib::SyncableThreadExecutor>    _summaryExecutor;
    SyncableExecutorThreadService                        _masterService;
    ExecutorThreadService                                _indexService;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _indexFieldInverter;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _indexFieldWriter;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _attributeFieldWriter;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _field_writer;
    vespalib::ISequencedTaskExecutor*                    _index_field_inverter_ptr;
    vespalib::ISequencedTaskExecutor*                    _index_field_writer_ptr;
    vespalib::ISequencedTaskExecutor*                    _attribute_field_writer_ptr;
    std::vector<Registration>                            _invokeRegistrations;

public:
    using OptimizeFor = vespalib::Executor::OptimizeFor;
    /**
     * Convenience constructor used in unit tests.
     */
    ExecutorThreadingService(vespalib::ThreadExecutor& sharedExecutor, uint32_t num_treads = 1);

    ExecutorThreadingService(vespalib::ThreadExecutor& sharedExecutor,
                             vespalib::ISequencedTaskExecutor* field_writer,
                             vespalib::InvokeService * invokeService,
                             const ThreadingServiceConfig& cfg,
                             uint32_t stackSize = 128 * 1024);
    ~ExecutorThreadingService() override;

    void blocking_master_execute(vespalib::Executor::Task::UP task) override;

    void shutdown();

    uint32_t master_task_limit() const {
        return _master_task_limit.load(std::memory_order_relaxed);
    }
    void set_task_limits(uint32_t master_task_limit,
                         uint32_t field_task_limit,
                         uint32_t summary_task_limit);

    searchcorespi::index::ISyncableThreadService &master() override {
        return _masterService;
    }
    searchcorespi::index::IThreadService &index() override {
        return _indexService;
    }

    vespalib::ThreadExecutor &summary() override {
        return *_summaryExecutor;
    }
    vespalib::ThreadExecutor &shared() override {
        return _sharedExecutor;
    }

    vespalib::ISequencedTaskExecutor &indexFieldInverter() override;
    vespalib::ISequencedTaskExecutor &indexFieldWriter() override;
    vespalib::ISequencedTaskExecutor &attributeFieldWriter() override;
    ExecutorThreadingServiceStats getStats();
};

}


