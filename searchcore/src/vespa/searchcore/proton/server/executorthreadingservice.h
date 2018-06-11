// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_thread_service.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

namespace proton {

class ExecutorThreadingServiceStats;

/**
 * Implementation of IThreadingService using 2 underlying thread stack executors
 * with 1 thread each.
 */
class ExecutorThreadingService : public searchcorespi::index::IThreadingService
{
private:
    vespalib::ThreadStackExecutor _masterExecutor;
    vespalib::BlockingThreadStackExecutor _indexExecutor;
    vespalib::BlockingThreadStackExecutor _summaryExecutor;
    ExecutorThreadService _masterService;
    ExecutorThreadService _indexService;
    ExecutorThreadService _summaryService;
    std::unique_ptr<search::ISequencedTaskExecutor> _indexFieldInverter;
    std::unique_ptr<search::ISequencedTaskExecutor> _indexFieldWriter;
    std::unique_ptr<search::ISequencedTaskExecutor> _attributeFieldWriter;

public:
    /**
     * Constructor.
     *
     * @stackSize The size of the stack of the underlying executors.
     * @taskLimit The task limit for the index executor.
     */
    ExecutorThreadingService(uint32_t threads = 1,
                             uint32_t stackSize = 128 * 1024,
                             uint32_t taskLimit = 1000);
    ~ExecutorThreadingService() override;

    /**
     * Implements vespalib::Syncable
     */
    vespalib::Syncable &sync() override;

    void shutdown();

    void setTaskLimit(uint32_t taskLimit, uint32_t summaryTaskLimit);

    // Expose the underlying executors for stats fetching and testing.
    vespalib::ThreadStackExecutorBase &getMasterExecutor() {
        return _masterExecutor;
    }
    vespalib::ThreadStackExecutorBase &getIndexExecutor() {
        return _indexExecutor;
    }
    vespalib::ThreadStackExecutorBase &getSummaryExecutor() {
        return _summaryExecutor;
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

    search::ISequencedTaskExecutor &indexFieldInverter() override {
        return *_indexFieldInverter;
    }

    search::ISequencedTaskExecutor &indexFieldWriter() override {
        return *_indexFieldWriter;
    }

    search::ISequencedTaskExecutor &attributeFieldWriter() override {
        return *_attributeFieldWriter;
    }

    ExecutorThreadingServiceStats getStats();
};

} // namespace proton


