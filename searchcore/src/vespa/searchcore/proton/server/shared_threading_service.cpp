// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_threading_service.h"
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>

VESPA_THREAD_STACK_TAG(proton_shared_executor)
VESPA_THREAD_STACK_TAG(proton_warmup_executor)

namespace proton {

SharedThreadingService::SharedThreadingService(const SharedThreadingServiceConfig& cfg)
    : _warmup(cfg.warmup_threads(), 128_Ki, proton_warmup_executor),
      _shared(std::make_shared<vespalib::BlockingThreadStackExecutor>(cfg.shared_threads(), 128_Ki,
                                                                      cfg.shared_task_limit(), proton_shared_executor))
{
}

}
