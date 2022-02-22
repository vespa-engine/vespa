// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "transport_helper.h"
#include <vespa/searchcore/proton/server/i_shared_threading_service.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>

namespace proton {

class MockSharedThreadingService : public ISharedThreadingService {
private:
    using ThreadExecutor = vespalib::ThreadExecutor;
    ThreadExecutor              & _warmup;
    ThreadExecutor              & _shared;
    vespalib::InvokeServiceImpl   _invokeService;
    Transport                     _transport;

public:
    MockSharedThreadingService(ThreadExecutor& warmup_in,
                               ThreadExecutor& shared_in);
    ~MockSharedThreadingService() override;
    ThreadExecutor& warmup() override { return _warmup; }
    ThreadExecutor& shared() override { return _shared; }
    vespalib::ISequencedTaskExecutor* field_writer() override { return nullptr; }
    vespalib::InvokeService & invokeService() override { return _invokeService; }
    FNET_Transport & transport() override { return _transport.transport(); }
};

}
