// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
using vespalib::steady_time;
using vespalib::SyncableThreadExecutor;
using vespalib::ThreadStackExecutor;
using OptimizeFor = vespalib::Executor::OptimizeFor;

namespace proton {

namespace {

[[nodiscard]] std::unique_ptr<SyncableThreadExecutor>
createExecutorWithOneThread(const ThreadingServiceConfig& cfg, vespalib::Runnable::init_fun_t init_function) {
    uint32_t taskLimit = cfg.defaultTaskLimit();
    if (cfg.optimize() == OptimizeFor::THROUGHPUT) {
        uint32_t watermark = (cfg.kindOfwatermark() == 0) ? taskLimit / 10 : cfg.kindOfwatermark();
        return std::make_unique<SingleExecutor>(std::move(init_function), taskLimit, cfg.is_task_limit_hard(),
                                                watermark, 100ms);
    } else {
        if (cfg.is_task_limit_hard()) {
            return std::make_unique<BlockingThreadStackExecutor>(1, taskLimit, std::move(init_function));
        } else {
            return std::make_unique<ThreadStackExecutor>(1, std::move(init_function));
        }
    }
}

VESPA_THREAD_STACK_TAG(main_executor)
VESPA_THREAD_STACK_TAG(index_executor)
VESPA_THREAD_STACK_TAG(summary_executor)

} // namespace

ExecutorThreadingService::ExecutorThreadingService(vespalib::Executor& sharedExecutor, FNET_Transport& transport,
                                                   vespalib::ISequencedTaskExecutor& field_writer)
    : ExecutorThreadingService(sharedExecutor, transport, field_writer, nullptr, ThreadingServiceConfig::make()) {
}

ExecutorThreadingService::ExecutorThreadingService(vespalib::Executor& sharedExecutor, FNET_Transport& transport,
                                                   vespalib::ISequencedTaskExecutor& field_writer,
                                                   vespalib::InvokeService*          invokerService,
                                                   const ThreadingServiceConfig&     cfg)

    : _sharedExecutor(sharedExecutor),
      _transport(transport),
      _main_executor(1, CpuUsage::wrap(main_executor, CpuUsage::Category::WRITE)),
      _main_task_limit(cfg.main_task_limit()),
      _indexExecutor(createExecutorWithOneThread(cfg, CpuUsage::wrap(index_executor, CpuUsage::Category::WRITE))),
      _summaryExecutor(createExecutorWithOneThread(cfg, CpuUsage::wrap(summary_executor, CpuUsage::Category::WRITE))),
      _main_service(_main_executor),
      _indexService(*_indexExecutor),
      _field_writer(field_writer),
      _invokeRegistrations() {
    if (cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT && invokerService) {
        _invokeRegistrations.push_back(
            invokerService->registerInvoke([executor = _indexExecutor.get()]() { executor->wakeup(); }));
        _invokeRegistrations.push_back(
            invokerService->registerInvoke([executor = _summaryExecutor.get()]() { executor->wakeup(); }));
    }
}

ExecutorThreadingService::~ExecutorThreadingService() = default;

void ExecutorThreadingService::blocking_main_execute(std::unique_ptr<vespalib::Executor::Task> task) noexcept {
    uint32_t limit = main_task_limit();
    if (limit > 0) {
        _main_executor.wait_for_task_count(limit);
    }
    _main_executor.execute(std::move(task));
}

void ExecutorThreadingService::shutdown() {
    _main_executor.shutdown().sync();
    _field_writer.sync_all();
    _summaryExecutor->shutdown().sync();
    _indexExecutor->shutdown().sync();
    _field_writer.sync_all();
    _field_writer.sync_all();
}

void ExecutorThreadingService::set_task_limits(uint32_t main_task_limit, uint32_t field_task_limit,
                                               uint32_t summary_task_limit) {
    _main_task_limit.store(main_task_limit, std::memory_order_release);
    _indexExecutor->setTaskLimit(field_task_limit);
    _summaryExecutor->setTaskLimit(summary_task_limit);
    _field_writer.setTaskLimit(field_task_limit);
}

[[nodiscard]] ExecutorThreadingServiceStats ExecutorThreadingService::getStats() {
    auto main_stats = _main_executor.getStats();
    auto index_stats = _indexExecutor->getStats();
    auto summary_stats = _summaryExecutor->getStats();
    return ExecutorThreadingServiceStats(main_stats, index_stats, summary_stats);
}

[[nodiscard]] vespalib::ISequencedTaskExecutor& ExecutorThreadingService::field_writer() noexcept {
    return _field_writer;
}

} // namespace proton
