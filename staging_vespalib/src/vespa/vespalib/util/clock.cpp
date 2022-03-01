// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clock.h"
#include <vespa/vespalib/util/invokeservice.h>

namespace vespalib {

Clock::Clock() :
     _timeNS(0u),
     _running(false),
     _invokeRegistration()
{
    setTime();
}

Clock::~Clock() = default;

void
Clock::setTime() const
{
    setTime(steady_clock::now());
}
void
Clock::setTime(steady_time now) const
{
    _timeNS.store(count_ns(now.time_since_epoch()), std::memory_order_relaxed);
}

void
Clock::start(InvokeService & invoker)
{
    _running.store(true, std::memory_order_relaxed);
    _invokeRegistration = invoker.registerInvoke([this](steady_time now) { setTime(now); });
}

void
Clock::stop()
{
    _running.store(false, std::memory_order_relaxed);
    _invokeRegistration.reset();
}

}
