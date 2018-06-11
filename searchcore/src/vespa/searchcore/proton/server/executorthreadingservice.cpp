// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executorthreadingservice.h"
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>

using vespalib::ThreadStackExecutorBase;
using search::SequencedTaskExecutor;

namespace proton {

    SequencedTaskExecutor & cast(search::ISequencedTaskExecutor & executor) {
        return static_cast<SequencedTaskExecutor &>(executor);
    }

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
    cast(*_indexFieldInverter).setTaskLimit(taskLimit);
    cast(*_indexFieldWriter).setTaskLimit(taskLimit);
    cast(*_attributeFieldWriter).setTaskLimit(taskLimit);
}

ExecutorThreadingServiceStats
ExecutorThreadingService::getStats()
{
    return ExecutorThreadingServiceStats(_masterExecutor.getStats(),
                                         _indexExecutor.getStats(),
                                         _summaryExecutor.getStats(),
                                         cast(*_indexFieldInverter).getStats(),
                                         cast(*_indexFieldWriter).getStats(),
                                         cast(*_attributeFieldWriter).getStats());
}

} // namespace proton

