// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_helper.h"
#include <vespa/fnet/transport.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

namespace proton {

Transport::Transport()
    : _transport(std::make_unique<FNET_Transport>()),
      _clock(std::make_unique<vespalib::TestClock>())
{
    _transport->Start();
}

Transport::~Transport() {
    shutdown();
}

const vespalib::Clock &
Transport::clock() const {
    return _clock->clock();
}
void
Transport::shutdown() {
    _transport->ShutDown(true);
}

VESPA_THREAD_STACK_TAG(proton_transport_and_executor_field_writer)

TransportAndExecutor::TransportAndExecutor(size_t num_threads)
    : Transport(),
      _sharedExecutor(std::make_unique<vespalib::ThreadStackExecutor>(num_threads)),
      _field_writer(vespalib::SequencedTaskExecutor::create(proton_transport_and_executor_field_writer, num_threads))
{}

TransportAndExecutor::~TransportAndExecutor() = default;

void
TransportAndExecutor::shutdown() {
    Transport::shutdown();
}

TransportAndExecutorService::TransportAndExecutorService(size_t num_threads)
    : TransportAndExecutor(num_threads),
      _writeService(std::make_unique<ExecutorThreadingService>(shared(), transport(), clock(), field_writer()))
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
