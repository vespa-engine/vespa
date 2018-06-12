// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executorthreadingservice.h"
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>

using vespalib::ThreadStackExecutorBase;
using search::SequencedTaskExecutor;

namespace proton {

ExecutorThreadingService::ExecutorThreadingService(uint32_t threads, uint32_t stackSize, uint32_t taskLimit)

    : _masterExecutor(1, stackSize),
      _indexExecutor(1, stackSize, taskLimit),
      _summaryExecutor(1, stackSize, taskLimit),
      _masterService(_masterExecutor),
      _indexService(_indexExecutor),
      _summaryService(_summaryExecutor),
      _indexFieldInverter(std::make_unique<SequencedTaskExecutor>(threads, taskLimit)),
      _indexFieldWriter(std::make_unique<SequencedTaskExecutor>(threads, taskLimit)),
      _attributeFieldWriter(std::make_unique<SequencedTaskExecutor>(threads, taskLimit))
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
    _indexExecutor.sync();
    _summaryExecutor.sync();
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
    _summaryExecutor.shutdown();
    _summaryExecutor.sync();
    _indexExecutor.shutdown();
    _indexExecutor.sync();
    _indexFieldInverter->sync();
    _indexFieldWriter->sync();
}

void
ExecutorThreadingService::setTaskLimit(uint32_t taskLimit, uint32_t summaryTaskLimit)
{
    _indexExecutor.setTaskLimit(taskLimit);
    _summaryExecutor.setTaskLimit(summaryTaskLimit);
    _indexFieldInverter->setTaskLimit(taskLimit);
    _indexFieldWriter->setTaskLimit(taskLimit);
    _attributeFieldWriter->setTaskLimit(taskLimit);
}

ExecutorThreadingServiceStats
ExecutorThreadingService::getStats()
{
    return ExecutorThreadingServiceStats(_masterExecutor.getStats(),
                                         _indexExecutor.getStats(),
                                         _summaryExecutor.getStats(),
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

