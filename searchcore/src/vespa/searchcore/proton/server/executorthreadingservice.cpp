// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executorthreadingservice.h"
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>


using vespalib::SyncableThreadExecutor;
using vespalib::BlockingThreadStackExecutor;
using vespalib::SingleExecutor;
using search::SequencedTaskExecutor;
using OptimizeFor = vespalib::Executor::OptimizeFor;

namespace proton {

namespace {

std::unique_ptr<SyncableThreadExecutor>
createExecutorWithOneThread(uint32_t stackSize, uint32_t taskLimit, OptimizeFor optimize) {
    if (optimize == OptimizeFor::THROUGHPUT) {
        return std::make_unique<SingleExecutor>(taskLimit);
    } else {
        return std::make_unique<BlockingThreadStackExecutor>(1, stackSize, taskLimit);
    }
}

}

ExecutorThreadingService::ExecutorThreadingService(vespalib::SyncableThreadExecutor & sharedExecutor,
                                                   uint32_t threads, uint32_t stackSize, uint32_t taskLimit,
                                                   OptimizeFor optimize)

    : _sharedExecutor(sharedExecutor),
      _masterExecutor(1, stackSize),
      _indexExecutor(createExecutorWithOneThread(stackSize, taskLimit, optimize)),
      _summaryExecutor(createExecutorWithOneThread(stackSize, taskLimit, optimize)),
      _masterService(_masterExecutor),
      _indexService(*_indexExecutor),
      _summaryService(*_summaryExecutor),
      _indexFieldInverter(SequencedTaskExecutor::create(threads, taskLimit)),
      _indexFieldWriter(SequencedTaskExecutor::create(threads, taskLimit)),
      _attributeFieldWriter(SequencedTaskExecutor::create(threads, taskLimit, optimize))
{
}

ExecutorThreadingService::~ExecutorThreadingService() = default;

vespalib::Syncable &
ExecutorThreadingService::sync()
{
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
    return *this;
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

search::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldInverter() {
    return *_indexFieldInverter;
}

search::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldWriter() {
    return *_indexFieldWriter;
}

search::ISequencedTaskExecutor &
ExecutorThreadingService::attributeFieldWriter() {
    return *_attributeFieldWriter;
}

} // namespace proton

