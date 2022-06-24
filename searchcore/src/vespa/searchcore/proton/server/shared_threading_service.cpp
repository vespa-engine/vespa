// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_threading_service.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/nice.h>

using vespalib::CpuUsage;
using vespalib::steady_time;

VESPA_THREAD_STACK_TAG(proton_field_writer_executor)
VESPA_THREAD_STACK_TAG(proton_shared_executor)
VESPA_THREAD_STACK_TAG(proton_warmup_executor)

namespace proton {

SharedThreadingService::SharedThreadingService(const SharedThreadingServiceConfig& cfg,
                                               FNET_Transport& transport,
                                               storage::spi::BucketExecutor& bucket_executor)
    : _transport(transport),
      _warmup(std::make_unique<vespalib::ThreadStackExecutor>(cfg.warmup_threads(), 128_Ki,
                                                              CpuUsage::wrap(proton_warmup_executor, CpuUsage::Category::COMPACT),
                                                              cfg.shared_task_limit())),
      _shared(std::make_shared<vespalib::BlockingThreadStackExecutor>(cfg.shared_threads(), 128_Ki,
                                                                      cfg.shared_task_limit(), vespalib::be_nice(proton_shared_executor, cfg.feeding_niceness()))),
      _field_writer(),
      _invokeService(std::max(vespalib::adjustTimeoutByDetectedHz(1ms),
                              cfg.field_writer_config().reactionTime())),
      _invokeRegistrations(),
      _bucket_executor(bucket_executor),
      _clock(_invokeService.nowRef())
{
    const auto& fw_cfg = cfg.field_writer_config();
    _field_writer = vespalib::SequencedTaskExecutor::create(vespalib::be_nice(CpuUsage::wrap(proton_field_writer_executor, CpuUsage::Category::WRITE), cfg.feeding_niceness()),
                                                            cfg.field_writer_threads(),
                                                            fw_cfg.defaultTaskLimit(),
                                                            fw_cfg.is_task_limit_hard(),
                                                            fw_cfg.optimize(),
                                                            fw_cfg.kindOfwatermark());
    if (fw_cfg.optimize() == vespalib::Executor::OptimizeFor::THROUGHPUT) {
        _invokeRegistrations.push_back(_invokeService.registerInvoke([executor = _field_writer.get()]() {
            executor->wakeup();
        }));
    }
}

SharedThreadingService::~SharedThreadingService() = default;

void
SharedThreadingService::sync_all_executors() {
    _warmup->sync();
    _shared->sync();
    if (_field_writer) {
        _field_writer->sync_all();
    }
}

}
