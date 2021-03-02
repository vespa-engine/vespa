// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_thread_service.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

namespace proton {

class ExecutorThreadingServiceStats;
class ThreadingServiceConfig;

/**
 * Implementation of IThreadingService using 2 underlying thread stack executors
 * with 1 thread each.
 */
class ExecutorThreadingService : public searchcorespi::index::IThreadingService
{
private:
    vespalib::ThreadExecutor                   & _sharedExecutor;
    vespalib::ThreadStackExecutor                        _masterExecutor;
    std::unique_ptr<vespalib::SyncableThreadExecutor>    _indexExecutor;
    std::unique_ptr<vespalib::SyncableThreadExecutor>    _summaryExecutor;
    ExecutorThreadService                                _masterService;
    ExecutorThreadService                                _indexService;
    ExecutorThreadService                                _summaryService;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _indexFieldInverter;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _indexFieldWriter;
    std::unique_ptr<vespalib::ISequencedTaskExecutor>    _attributeFieldWriter;

    void syncOnce();
public:
    using OptimizeFor = vespalib::Executor::OptimizeFor;
    /**
     * Constructor.
     *
     * @stackSize The size of the stack of the underlying executors.
     * @cfg config used to set up all executors.
     */
    ExecutorThreadingService(vespalib::ThreadExecutor &sharedExecutor,
                             const ThreadingServiceConfig & cfg, uint32_t stackSize = 128 * 1024);
    ExecutorThreadingService(vespalib::ThreadExecutor &sharedExecutor, uint32_t num_treads = 1);
    ~ExecutorThreadingService() override;

    /**
     * Implements vespalib::Syncable
     */
    vespalib::Syncable &sync() override;

    void shutdown();

    void setTaskLimit(uint32_t taskLimit, uint32_t summaryTaskLimit);

    // Expose the underlying executors for stats fetching and testing.
    // TOD: Remove - This is only used for casting to check the underlying type
    vespalib::ThreadExecutor &getMasterExecutor() {
        return _masterExecutor;
    }
    vespalib::ThreadExecutor &getIndexExecutor() {
        return *_indexExecutor;
    }
    vespalib::ThreadExecutor &getSummaryExecutor() {
        return *_summaryExecutor;
    }

    /**
     * Implements IThreadingService
     */
    searchcorespi::index::IThreadService &master() override {
        return _masterService;
    }
    searchcorespi::index::IThreadService &index() override {
        return _indexService;
    }

    searchcorespi::index::IThreadService &summary() override {
        return _summaryService;
    }
    vespalib::ThreadExecutor &shared() override {
        return _sharedExecutor;
    }

    vespalib::ISequencedTaskExecutor &indexFieldInverter() override;
    vespalib::ISequencedTaskExecutor &indexFieldWriter() override;
    vespalib::ISequencedTaskExecutor &attributeFieldWriter() override;
    ExecutorThreadingServiceStats getStats();
};

} // namespace proton


