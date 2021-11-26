// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/i_shared_threading_service.h>

namespace proton {

class MockSharedThreadingService : public ISharedThreadingService {
private:
    vespalib::ThreadExecutor& _warmup;
    vespalib::ThreadExecutor& _shared;

public:
    MockSharedThreadingService(vespalib::ThreadExecutor& warmup_in,
                               vespalib::ThreadExecutor& shared_in)
        : _warmup(warmup_in),
          _shared(shared_in)
    {}
    vespalib::ThreadExecutor& warmup() override { return _warmup; }
    vespalib::ThreadExecutor& shared() override { return _shared; }
};

}
