// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
using SharedFieldWriterExecutor = proton::ThreadingServiceConfig::SharedFieldWriterExecutor;

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
VESPA_THREAD_STACK_TAG(index_field_inverter_executor)
VESPA_THREAD_STACK_TAG(index_field_writer_executor)
VESPA_THREAD_STACK_TAG(attribute_field_writer_executor)
VESPA_THREAD_STACK_TAG(field_writer_executor)

}

ExecutorThreadingService::ExecutorThreadingService(vespalib::ThreadExecutor &sharedExecutor, uint32_t num_treads)
    : ExecutorThreadingService(sharedExecutor, ThreadingServiceConfig::make(num_treads))
{}

ExecutorThreadingService::ExecutorThreadingService(vespalib::ThreadExecutor& sharedExecutor,
                                                   const ThreadingServiceConfig& cfg,
                                                   uint32_t stackSize)

    : _sharedExecutor(sharedExecutor),
      _masterExecutor(1, stackSize, master_executor),
      _shared_field_writer(cfg.shared_field_writer()),
      _master_task_limit(cfg.master_task_limit()),
      _indexExecutor(createExecutorWithOneThread(stackSize, cfg.defaultTaskLimit(), cfg.optimize(), index_executor)),
      _summaryExecutor(createExecutorWithOneThread(stackSize, cfg.defaultTaskLimit(), cfg.optimize(), summary_executor)),
      _masterService(_masterExecutor),
      _indexService(*_indexExecutor),
      _indexFieldInverter(),
      _indexFieldWriter(),
      _attributeFieldWriter(),
      _field_writer(),
      _index_field_inverter_ptr(),
      _index_field_writer_ptr(),
      _attribute_field_writer_ptr()
{
    if (_shared_field_writer == SharedFieldWriterExecutor::INDEX) {
        _field_writer = SequencedTaskExecutor::create(field_writer_executor, cfg.indexingThreads() * 2, cfg.defaultTaskLimit());
        _attributeFieldWriter = SequencedTaskExecutor::create(attribute_field_writer_executor, cfg.indexingThreads(), cfg.defaultTaskLimit(),
                                                              cfg.optimize(), cfg.kindOfwatermark(), cfg.reactionTime());
        _index_field_inverter_ptr = _field_writer.get();
        _index_field_writer_ptr = _field_writer.get();
        _attribute_field_writer_ptr = _attributeFieldWriter.get();

    } else if (_shared_field_writer == SharedFieldWriterExecutor::INDEX_AND_ATTRIBUTE) {
        _field_writer = SequencedTaskExecutor::create(field_writer_executor, cfg.indexingThreads() * 3, cfg.defaultTaskLimit(),
                                                      cfg.optimize(), cfg.kindOfwatermark(), cfg.reactionTime());
        _index_field_inverter_ptr = _field_writer.get();
        _index_field_writer_ptr = _field_writer.get();
        _attribute_field_writer_ptr = _field_writer.get();
    } else {
        // TODO: Add support for shared field writer across all document dbs.
        _indexFieldInverter = SequencedTaskExecutor::create(index_field_inverter_executor, cfg.indexingThreads(), cfg.defaultTaskLimit());
        _indexFieldWriter = SequencedTaskExecutor::create(index_field_writer_executor, cfg.indexingThreads(), cfg.defaultTaskLimit());
        _attributeFieldWriter = SequencedTaskExecutor::create(attribute_field_writer_executor, cfg.indexingThreads(), cfg.defaultTaskLimit(),
                                                              cfg.optimize(), cfg.kindOfwatermark(), cfg.reactionTime());
        _index_field_inverter_ptr = _indexFieldInverter.get();
        _index_field_writer_ptr = _indexFieldWriter.get();
        _attribute_field_writer_ptr = _attributeFieldWriter.get();
    }
}

ExecutorThreadingService::~ExecutorThreadingService() = default;

void
ExecutorThreadingService::sync_all_executors() {
    // We have multiple patterns where task A posts to B which post back to A
    for (size_t i = 0; i < 2; i++) {
        syncOnce();
    }
}

void
ExecutorThreadingService::blocking_master_execute(vespalib::Executor::Task::UP task)
{
    uint32_t limit = master_task_limit();
    if (limit > 0) {
        _masterExecutor.wait_for_task_count(limit);
    }
    _masterExecutor.execute(std::move(task));
}

void
ExecutorThreadingService::syncOnce() {
    bool isMasterThread = _masterService.isCurrentThread();
    if (!isMasterThread) {
        _masterExecutor.sync();
    }
    _attribute_field_writer_ptr->sync_all();
    _indexExecutor->sync();
    _summaryExecutor->sync();
    _index_field_inverter_ptr->sync_all();
    _index_field_writer_ptr->sync_all();
    if (!isMasterThread) {
        _masterExecutor.sync();
    }
}

void
ExecutorThreadingService::shutdown()
{
    _masterExecutor.shutdown().sync();
    _attribute_field_writer_ptr->sync_all();
    _summaryExecutor->shutdown().sync();
    _indexExecutor->shutdown().sync();
    _index_field_inverter_ptr->sync_all();
    _index_field_writer_ptr->sync_all();
}

void
ExecutorThreadingService::set_task_limits(uint32_t master_task_limit,
                                          uint32_t field_task_limit,
                                          uint32_t summary_task_limit)
{
    _master_task_limit.store(master_task_limit, std::memory_order_release);
    _indexExecutor->setTaskLimit(field_task_limit);
    _summaryExecutor->setTaskLimit(summary_task_limit);
    _index_field_inverter_ptr->setTaskLimit(field_task_limit);
    _index_field_writer_ptr->setTaskLimit(field_task_limit);
    _attribute_field_writer_ptr->setTaskLimit(field_task_limit);
}

ExecutorThreadingServiceStats
ExecutorThreadingService::getStats()
{
    auto master_stats = _masterExecutor.getStats();
    auto index_stats = _indexExecutor->getStats();
    auto summary_stats = _summaryExecutor->getStats();
    if (_shared_field_writer == SharedFieldWriterExecutor::INDEX) {
        auto field_writer_stats = _field_writer->getStats();
        return ExecutorThreadingServiceStats(master_stats, index_stats, summary_stats,
                                             field_writer_stats,
                                             field_writer_stats,
                                             _attribute_field_writer_ptr->getStats());
    } else if (_shared_field_writer == SharedFieldWriterExecutor::INDEX_AND_ATTRIBUTE) {
        auto field_writer_stats = _field_writer->getStats();
        return ExecutorThreadingServiceStats(master_stats, index_stats, summary_stats,
                                             field_writer_stats,
                                             field_writer_stats,
                                             field_writer_stats);
    } else {
        return ExecutorThreadingServiceStats(master_stats, index_stats, summary_stats,
                                             _index_field_inverter_ptr->getStats(),
                                             _index_field_writer_ptr->getStats(),
                                             _attribute_field_writer_ptr->getStats());
    }
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldInverter() {
    return *_index_field_inverter_ptr;
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldWriter() {
    return *_index_field_writer_ptr;
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::attributeFieldWriter() {
    return *_attribute_field_writer_ptr;
}

} // namespace proton

