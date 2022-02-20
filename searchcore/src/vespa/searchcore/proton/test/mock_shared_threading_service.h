// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/i_shared_threading_service.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/util/size_literals.h>

namespace proton {

class MockSharedThreadingService : public ISharedThreadingService {
private:
    vespalib::ThreadExecutor  & _warmup;
    vespalib::ThreadExecutor  & _shared;
    vespalib::InvokeServiceImpl _invokeService;
    FastOS_ThreadPool           _threadPool;
    FNET_Transport              _transport;

public:
    MockSharedThreadingService(vespalib::ThreadExecutor& warmup_in,
                               vespalib::ThreadExecutor& shared_in)
        : _warmup(warmup_in),
          _shared(shared_in),
          _invokeService(10ms),
          _threadPool(64_Ki),
          _transport()
    {
        _transport.Start(&_threadPool);
    }
    ~MockSharedThreadingService() {
        _transport.ShutDown(true);
    }
    vespalib::ThreadExecutor& warmup() override { return _warmup; }
    vespalib::ThreadExecutor& shared() override { return _shared; }
    vespalib::ISequencedTaskExecutor* field_writer() override { return nullptr; }
    vespalib::InvokeService & invokeService() override { return _invokeService; }
    FNET_Transport & transport() override { return _transport; }
};

}
