// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_shared_threading_service.h"

namespace proton {

MockSharedThreadingService::MockSharedThreadingService(ThreadExecutor& warmup_in, ThreadExecutor& shared_in)
    : _warmup(warmup_in),
      _shared(shared_in),
      _invokeService(10ms),
      _transport()
{
}

MockSharedThreadingService::~MockSharedThreadingService() = default;

}
