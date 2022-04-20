// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_shared_threading_service.h"
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

VESPA_THREAD_STACK_TAG(mock_field_writer_executor)

namespace proton {

MockSharedThreadingService::MockSharedThreadingService(ThreadExecutor& warmup_in,
                                                       ThreadExecutor& shared_in,
                                                       size_t num_bucket_executors)
    : _warmup(warmup_in),
      _shared(shared_in),
      _field_writer(vespalib::SequencedTaskExecutor::create(mock_field_writer_executor, 1)),
      _invokeService(10ms),
      _transport(),
      _bucket_executor(num_bucket_executors),
      _clock(_invokeService.nowRef())
{
}

MockSharedThreadingService::~MockSharedThreadingService() = default;

}
