// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_threading_service.h"
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>

VESPA_THREAD_STACK_TAG(proton_field_writer_executor)
VESPA_THREAD_STACK_TAG(proton_shared_executor)
VESPA_THREAD_STACK_TAG(proton_warmup_executor)

namespace proton {

using SharedFieldWriterExecutor = ThreadingServiceConfig::ProtonConfig::Feeding::SharedFieldWriterExecutor;

SharedThreadingService::SharedThreadingService(const SharedThreadingServiceConfig& cfg)
    : _warmup(cfg.warmup_threads(), 128_Ki, proton_warmup_executor),
      _shared(std::make_shared<vespalib::BlockingThreadStackExecutor>(cfg.shared_threads(), 128_Ki,
                                                                      cfg.shared_task_limit(), proton_shared_executor)),
      _field_writer()
{
    const auto& fw_cfg = cfg.field_writer_config();
    if (fw_cfg.shared_field_writer() == SharedFieldWriterExecutor::DOCUMENT_DB) {
        _field_writer = vespalib::SequencedTaskExecutor::create(proton_field_writer_executor,
                                                                fw_cfg.indexingThreads() * 3,
                                                                fw_cfg.defaultTaskLimit(),
                                                                fw_cfg.optimize(),
                                                                fw_cfg.kindOfwatermark(),
                                                                fw_cfg.reactionTime());
    }
}

SharedThreadingService::~SharedThreadingService() = default;

void
SharedThreadingService::sync_all_executors() {
    _warmup.sync();
    _shared->sync();
    if (_field_writer) {
        _field_writer->sync_all();
    }
}

}
