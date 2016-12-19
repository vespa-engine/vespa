// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "clock.h"

using namespace fastos;

namespace vespalib {


Clock::Clock(double timePeriod) :
     _timeNS(0u),
     _timePeriodMS(static_cast<uint32_t>(timePeriod*1000)),
     _cond(),
     _stop(false),
     _running(false)
{
    setTime();
}

Clock::~Clock()
{
    assert(!_running);
}

void Clock::setTime() const
{
    _timeNS = ClockSystem::adjustTick2Sec(ClockSystem::now());
}

void Clock::Run(FastOS_ThreadInterface *thread, void *arguments)
{
    (void) arguments;
    _running = true;
    _cond.Lock();
    while ( ! thread->GetBreakFlag() && !_stop) {
        setTime();
        _cond.TimedWait(_timePeriodMS);
    }
    _cond.Unlock();
    _running = false;
}

void
Clock::stop(void)
{
    _cond.Lock();
    _stop = true;
    _cond.Broadcast();
    _cond.Unlock();
}

}
