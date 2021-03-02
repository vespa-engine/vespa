// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executorthreadingservice.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>

using vespalib::SyncableThreadExecutor;
using vespalib::BlockingThreadStackExecutor;
using vespalib::SingleExecutor;
using vespalib::SequencedTaskExecutor;
using OptimizeFor = vespalib::Executor::OptimizeFor;

namespace proton {

namespace {

std::unique_ptr<SyncableThreadExecutor>
createExecutorWithOneThread(uint32_t stackSize, uint32_t taskLimit, OptimizeFor optimize,
                            vespalib::Runnable::init_fun_t init_function) {
    if (optimize == OptimizeFor::THROUGHPUT) {
        return std::make_unique<SingleExecutor>(std::move(init_function), taskLimit);
    } else {
        return std::make_unique<BlockingThreadStackExecutor>(1, stackSize, taskLimit, std::move(init_function));
    }
}

VESPA_THREAD_STACK_TAG(master_executor)
VESPA_THREAD_STACK_TAG(index_executor)
VESPA_THREAD_STACK_TAG(summary_executor)
VESPA_THREAD_STACK_TAG(field_inverter_executor)
VESPA_THREAD_STACK_TAG(field_writer_executor)
VESPA_THREAD_STACK_TAG(attribute_executor)

}

ExecutorThreadingService::ExecutorThreadingService(vespalib::ThreadExecutor &sharedExecutor, uint32_t num_treads)
    : ExecutorThreadingService(sharedExecutor, ThreadingServiceConfig::make(num_treads))
{}

ExecutorThreadingService::ExecutorThreadingService(vespalib::ThreadExecutor & sharedExecutor,
                                                   const ThreadingServiceConfig & cfg,  uint32_t stackSize)

    : _sharedExecutor(sharedExecutor),
      _masterExecutor(1, stackSize, master_executor),
      _indexExecutor(createExecutorWithOneThread(stackSize, cfg.defaultTaskLimit(), cfg.optimize(), index_executor)),
      _summaryExecutor(createExecutorWithOneThread(stackSize, cfg.defaultTaskLimit(), cfg.optimize(), summary_executor)),
      _masterService(_masterExecutor),
      _indexService(*_indexExecutor),
      _summaryService(*_summaryExecutor),
      _indexFieldInverter(SequencedTaskExecutor::create(field_inverter_executor, cfg.indexingThreads(), cfg.defaultTaskLimit())),
      _indexFieldWriter(SequencedTaskExecutor::create(field_writer_executor, cfg.indexingThreads(), cfg.defaultTaskLimit())),
      _attributeFieldWriter(SequencedTaskExecutor::create(attribute_executor, cfg.indexingThreads(), cfg.defaultTaskLimit(),
                                                          cfg.optimize(), cfg.kindOfwatermark(), cfg.reactionTime()))
{
}

ExecutorThreadingService::~ExecutorThreadingService() = default;

vespalib::Syncable &
ExecutorThreadingService::sync() {
    // We have multiple patterns where task A posts to B which post back to A
    for (size_t i = 0; i < 2; i++) {
        syncOnce();
    }
    return *this;
}

void
ExecutorThreadingService::syncOnce() {
    bool isMasterThread = _masterService.isCurrentThread();
    if (!isMasterThread) {
        _masterExecutor.sync();
    }
    _attributeFieldWriter->sync();
    _indexExecutor->sync();
    _summaryExecutor->sync();
    _indexFieldInverter->sync();
    _indexFieldWriter->sync();
    if (!isMasterThread) {
        _masterExecutor.sync();
    }
}

void
ExecutorThreadingService::shutdown()
{
    _masterExecutor.shutdown();
    _masterExecutor.sync();
    _attributeFieldWriter->sync();
    _summaryExecutor->shutdown();
    _summaryExecutor->sync();
    _indexExecutor->shutdown();
    _indexExecutor->sync();
    _indexFieldInverter->sync();
    _indexFieldWriter->sync();
}

void
ExecutorThreadingService::setTaskLimit(uint32_t taskLimit, uint32_t summaryTaskLimit)
{
    _indexExecutor->setTaskLimit(taskLimit);
    _summaryExecutor->setTaskLimit(summaryTaskLimit);
    _indexFieldInverter->setTaskLimit(taskLimit);
    _indexFieldWriter->setTaskLimit(taskLimit);
    _attributeFieldWriter->setTaskLimit(taskLimit);
}

ExecutorThreadingServiceStats
ExecutorThreadingService::getStats()
{
    return ExecutorThreadingServiceStats(_masterExecutor.getStats(),
                                         _indexExecutor->getStats(),
                                         _summaryExecutor->getStats(),
                                         _sharedExecutor.getStats(),
                                         _indexFieldInverter->getStats(),
                                         _indexFieldWriter->getStats(),
                                         _attributeFieldWriter->getStats());
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldInverter() {
    return *_indexFieldInverter;
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldWriter() {
    return *_indexFieldWriter;
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::attributeFieldWriter() {
    return *_attributeFieldWriter;
}

} // namespace proton

