// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clock.h"
#include <cassert>
#include <chrono>

namespace vespalib {


Clock::Clock(double timePeriod) :
     _timeNS(0u),
     _timePeriodMS(static_cast<uint32_t>(timePeriod*1000)),
     _lock(),
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
    _timeNS.store(fastos::ClockSteady::now() - fastos::SteadyTimeStamp::ZERO, std::memory_order_relaxed);
}

void Clock::Run(FastOS_ThreadInterface *thread, void *arguments)
{
    (void) arguments;
    _running = true;
    std::unique_lock<std::mutex> guard(_lock);
    while ( ! thread->GetBreakFlag() && !_stop) {
        setTime();
        _cond.wait_for(guard, std::chrono::milliseconds(_timePeriodMS));
    }
    _running = false;
}

void
Clock::stop()
{
    std::lock_guard<std::mutex> guard(_lock);
    _stop = true;
    _cond.notify_all();
}

}
