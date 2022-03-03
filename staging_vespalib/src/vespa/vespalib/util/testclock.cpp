// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testclock.h"
#include <vespa/vespalib/util/invokeserviceimpl.h>

namespace vespalib {

TestClock::TestClock()
    : _ticker(std::make_unique<InvokeServiceImpl>(10ms)),
      _clock(_ticker->nowRef())
{
}

TestClock::~TestClock() = default;

}
