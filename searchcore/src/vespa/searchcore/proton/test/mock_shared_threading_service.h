// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/i_shared_threading_service.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>

namespace proton {

class MockSharedThreadingService : public ISharedThreadingService {
private:
    vespalib::ThreadExecutor& _warmup;
    vespalib::ThreadExecutor& _shared;
    vespalib::InvokeServiceImpl _invokeService;

public:
    MockSharedThreadingService(vespalib::ThreadExecutor& warmup_in,
                               vespalib::ThreadExecutor& shared_in)
        : _warmup(warmup_in),
          _shared(shared_in),
          _invokeService(10ms)
    {}
    vespalib::ThreadExecutor& warmup() override { return _warmup; }
    vespalib::ThreadExecutor& shared() override { return _shared; }
    vespalib::ISequencedTaskExecutor* field_writer() override { return nullptr; }
    vespalib::InvokeService & invokeService() override { return _invokeService; }
};

}
