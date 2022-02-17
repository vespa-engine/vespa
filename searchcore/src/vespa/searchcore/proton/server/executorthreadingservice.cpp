// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executorthreadingservice.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>

using vespalib::BlockingThreadStackExecutor;
using vespalib::CpuUsage;
using vespalib::SequencedTaskExecutor;
using vespalib::SingleExecutor;
using vespalib::SyncableThreadExecutor;
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

ExecutorThreadingService::ExecutorThreadingService(vespalib::Executor &sharedExecutor, uint32_t num_treads)
    : ExecutorThreadingService(sharedExecutor, nullptr, nullptr, ThreadingServiceConfig::make(num_treads))
{}

ExecutorThreadingService::ExecutorThreadingService(vespalib::Executor& sharedExecutor,
                                                   vespalib::ISequencedTaskExecutor* field_writer,
                                                   vespalib::InvokeService * invokerService,
                                                   const ThreadingServiceConfig& cfg,
                                                   uint32_t stackSize)

    : _sharedExecutor(sharedExecutor),
      _masterExecutor(1, stackSize, CpuUsage::wrap(master_executor, CpuUsage::Category::WRITE)),
      _shared_field_writer(cfg.shared_field_writer()),
      _master_task_limit(cfg.master_task_limit()),
      _indexExecutor(createExecutorWithOneThread(stackSize, cfg.defaultTaskLimit(), cfg.optimize(),
                                                 CpuUsage::wrap(index_executor, CpuUsage::Category::WRITE))),
      _summaryExecutor(createExecutorWithOneThread(stackSize, cfg.defaultTaskLimit(), cfg.optimize(),
                                                   CpuUsage::wrap(summary_executor, CpuUsage::Category::WRITE))),
      _masterService(_masterExecutor),
      _indexService(*_indexExecutor),
      _indexFieldInverter(),
      _indexFieldWriter(),
      _attributeFieldWriter(),
      _field_writer(),
      _index_field_inverter_ptr(),
      _index_field_writer_ptr(),
      _attribute_field_writer_ptr(),
      _invokeRegistrations()
{
    if (cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT && invokerService) {
        _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_indexExecutor.get()](){ executor->wakeup();}));
        _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_summaryExecutor.get()](){ executor->wakeup();}));
    }
    if (_shared_field_writer == SharedFieldWriterExecutor::INDEX) {
        _field_writer = SequencedTaskExecutor::create(CpuUsage::wrap(field_writer_executor, CpuUsage::Category::WRITE),
                                                      cfg.indexingThreads() * 2, cfg.defaultTaskLimit());
        _attributeFieldWriter = SequencedTaskExecutor::create(CpuUsage::wrap(attribute_field_writer_executor, CpuUsage::Category::WRITE),
                                                              cfg.indexingThreads(), cfg.defaultTaskLimit(),
                                                              cfg.is_task_limit_hard(), cfg.optimize(), cfg.kindOfwatermark());
        if (cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT && invokerService) {
            _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_attributeFieldWriter.get()](){ executor->wakeup();}));
        }
        _index_field_inverter_ptr = _field_writer.get();
        _index_field_writer_ptr = _field_writer.get();
        _attribute_field_writer_ptr = _attributeFieldWriter.get();

    } else if (_shared_field_writer == SharedFieldWriterExecutor::INDEX_AND_ATTRIBUTE) {
        _field_writer = SequencedTaskExecutor::create(CpuUsage::wrap(field_writer_executor, CpuUsage::Category::WRITE),
                                                      cfg.indexingThreads() * 3, cfg.defaultTaskLimit(),
                                                      cfg.is_task_limit_hard(), cfg.optimize(), cfg.kindOfwatermark());
        if (cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT && invokerService) {
            _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_field_writer.get()](){ executor->wakeup();}));
        }
        _index_field_inverter_ptr = _field_writer.get();
        _index_field_writer_ptr = _field_writer.get();
        _attribute_field_writer_ptr = _field_writer.get();
    } else if (_shared_field_writer == SharedFieldWriterExecutor::DOCUMENT_DB) {
        assert(field_writer != nullptr);
        _index_field_inverter_ptr = field_writer;
        _index_field_writer_ptr = field_writer;
        _attribute_field_writer_ptr = field_writer;
    } else {
        _indexFieldInverter = SequencedTaskExecutor::create(CpuUsage::wrap(index_field_inverter_executor, CpuUsage::Category::WRITE),
                                                            cfg.indexingThreads(), cfg.defaultTaskLimit());
        _indexFieldWriter = SequencedTaskExecutor::create(CpuUsage::wrap(index_field_writer_executor, CpuUsage::Category::WRITE),
                                                          cfg.indexingThreads(), cfg.defaultTaskLimit());
        _attributeFieldWriter = SequencedTaskExecutor::create(CpuUsage::wrap(attribute_field_writer_executor, CpuUsage::Category::WRITE),
                                                              cfg.indexingThreads(), cfg.defaultTaskLimit(),
                                                              cfg.is_task_limit_hard(), cfg.optimize(), cfg.kindOfwatermark());
        if (cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT && invokerService) {
            _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_attributeFieldWriter.get()](){ executor->wakeup();}));
        }
        _index_field_inverter_ptr = _indexFieldInverter.get();
        _index_field_writer_ptr = _indexFieldWriter.get();
        _attribute_field_writer_ptr = _attributeFieldWriter.get();
    }
}

ExecutorThreadingService::~ExecutorThreadingService() = default;

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
    // TODO: Move this to a common place when the field writer is always shared.
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
    } else if (_shared_field_writer == SharedFieldWriterExecutor::DOCUMENT_DB) {
        vespalib::ExecutorStats empty_stats;
        // In this case the field writer stats are reported at a higher level.
        return ExecutorThreadingServiceStats(master_stats, index_stats, summary_stats,
                                             empty_stats, empty_stats, empty_stats);
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

