// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executorthreadingservice.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>

using vespalib::BlockingThreadStackExecutor;
using vespalib::ThreadStackExecutor;
using vespalib::CpuUsage;
using vespalib::SequencedTaskExecutor;
using vespalib::SingleExecutor;
using vespalib::SyncableThreadExecutor;
using vespalib::steady_time;
using OptimizeFor = vespalib::Executor::OptimizeFor;

namespace proton {

namespace {

std::unique_ptr<SyncableThreadExecutor>
createExecutorWithOneThread(const ThreadingServiceConfig & cfg, vespalib::Runnable::init_fun_t init_function) {
    uint32_t taskLimit = cfg.defaultTaskLimit();
    if (cfg.optimize() == OptimizeFor::THROUGHPUT) {
        uint32_t watermark = (cfg.kindOfwatermark() == 0) ? taskLimit / 10 : cfg.kindOfwatermark();
        return std::make_unique<SingleExecutor>(std::move(init_function), taskLimit, cfg.is_task_limit_hard(), watermark, 100ms);
    } else {
        if (cfg.is_task_limit_hard()) {
            return std::make_unique<BlockingThreadStackExecutor>(1, taskLimit, std::move(init_function));
        } else {
            return std::make_unique<ThreadStackExecutor>(1, std::move(init_function));
        }
    }
}

VESPA_THREAD_STACK_TAG(master_executor)
VESPA_THREAD_STACK_TAG(index_executor)
VESPA_THREAD_STACK_TAG(summary_executor)

}

ExecutorThreadingService::ExecutorThreadingService(vespalib::Executor& sharedExecutor,
                                                   FNET_Transport& transport,
                                                   const vespalib::Clock& clock,
                                                   vespalib::ISequencedTaskExecutor& field_writer)
    : ExecutorThreadingService(sharedExecutor, transport, clock, field_writer, nullptr, ThreadingServiceConfig::make())
{}

ExecutorThreadingService::ExecutorThreadingService(vespalib::Executor & sharedExecutor,
                                                   FNET_Transport & transport,
                                                   const vespalib::Clock & clock,
                                                   vespalib::ISequencedTaskExecutor& field_writer,
                                                   vespalib::InvokeService * invokerService,
                                                   const ThreadingServiceConfig & cfg)

    : _sharedExecutor(sharedExecutor),
      _transport(transport),
      _clock(clock),
      _masterExecutor(1, CpuUsage::wrap(master_executor, CpuUsage::Category::WRITE)),
      _master_task_limit(cfg.master_task_limit()),
      _indexExecutor(createExecutorWithOneThread(cfg, CpuUsage::wrap(index_executor, CpuUsage::Category::WRITE))),
      _summaryExecutor(createExecutorWithOneThread(cfg, CpuUsage::wrap(summary_executor, CpuUsage::Category::WRITE))),
      _masterService(_masterExecutor),
      _indexService(*_indexExecutor),
      _index_field_inverter(field_writer),
      _index_field_writer(field_writer),
      _attribute_field_writer(field_writer),
      _invokeRegistrations()
{
    if (cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT && invokerService) {
        _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_indexExecutor.get()](){ executor->wakeup();}));
        _invokeRegistrations.push_back(invokerService->registerInvoke([executor=_summaryExecutor.get()](){ executor->wakeup();}));
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
    _attribute_field_writer.sync_all();
    _summaryExecutor->shutdown().sync();
    _indexExecutor->shutdown().sync();
    _index_field_inverter.sync_all();
    _index_field_writer.sync_all();
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
    _index_field_inverter.setTaskLimit(field_task_limit);
    _index_field_writer.setTaskLimit(field_task_limit);
    _attribute_field_writer.setTaskLimit(field_task_limit);
}

ExecutorThreadingServiceStats
ExecutorThreadingService::getStats()
{
    auto master_stats = _masterExecutor.getStats();
    auto index_stats = _indexExecutor->getStats();
    auto summary_stats = _summaryExecutor->getStats();
    vespalib::ExecutorStats empty_stats;
    // In this case the field writer stats are reported at a higher level.
    return ExecutorThreadingServiceStats(master_stats, index_stats, summary_stats,
                                         empty_stats, empty_stats, empty_stats);
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldInverter() {
    return _index_field_inverter;
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::indexFieldWriter() {
    return _index_field_writer;
}

vespalib::ISequencedTaskExecutor &
ExecutorThreadingService::attributeFieldWriter() {
    return _attribute_field_writer;
}

} // namespace proton

