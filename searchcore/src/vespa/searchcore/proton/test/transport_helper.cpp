// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_helper.h"
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>

namespace proton {

Transport::Transport()
    : _threadPool(std::make_unique<FastOS_ThreadPool>(64_Ki)),
      _transport(std::make_unique<FNET_Transport>())
{
    _transport->Start(_threadPool.get());
}

Transport::~Transport() {
    shutdown();
}

void
Transport::shutdown() {
    _transport->ShutDown(true);
}

TransportAndExecutor::TransportAndExecutor(size_t num_threads)
    : Transport(),
      _sharedExecutor(std::make_unique<vespalib::ThreadStackExecutor>(num_threads, 64_Ki))
{}

TransportAndExecutor::~TransportAndExecutor() = default;

void
TransportAndExecutor::shutdown() {
    Transport::shutdown();
}

TransportAndExecutorService::TransportAndExecutorService(size_t num_threads)
    : TransportAndExecutor(num_threads),
      _writeService(std::make_unique<ExecutorThreadingService>(shared(), transport()))
{}
TransportAndExecutorService::~TransportAndExecutorService() = default;

searchcorespi::index::IThreadingService &
TransportAndExecutorService::write() {
    return *_writeService;
}

void TransportAndExecutorService::shutdown() {
    _writeService->shutdown();
    TransportAndExecutor::shutdown();
}
}
