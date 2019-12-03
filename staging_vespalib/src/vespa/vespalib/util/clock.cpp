// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clock.h"
#include <vespa/fastos/thread.h>
#include <mutex>
#include <condition_variable>
#include <cassert>
#include <chrono>

namespace vespalib {

namespace clock::internal {

class Updater : public FastOS_Runnable
{
private:
    Clock                   & _clock;
    int                       _timePeriodMS;
    std::mutex                _lock;
    std::condition_variable   _cond;
    bool                      _stop;


    void Run(FastOS_ThreadInterface *thisThread, void *arguments) override;

public:
    Updater(Clock & clock, double timePeriod=0.100);
    ~Updater();

    void stop();
};

Updater::Updater(Clock & clock, double timePeriod)
    : _clock(clock),
      _timePeriodMS(static_cast<uint32_t>(timePeriod*1000)),
      _lock(),
      _cond(),
      _stop(false)
{ }

Updater::~Updater() = default;

void
Updater::Run(FastOS_ThreadInterface *thread, void *arguments)
{
    (void) arguments;
    _clock._running = true;
    std::unique_lock<std::mutex> guard(_lock);
    while ( ! thread->GetBreakFlag() && !_stop) {
        _clock.setTime();
        _cond.wait_for(guard, std::chrono::milliseconds(_timePeriodMS));
    }
    _clock._running = false;
}

void
Updater::stop()
{
    std::lock_guard<std::mutex> guard(_lock);
    _stop = true;
    _cond.notify_all();
}

}

Clock::Clock(double timePeriod) :
     _timeNS(0u),
     _updater(std::make_unique<clock::internal::Updater>(*this, timePeriod)),
     _running(false)
{
    setTime();
}

Clock::~Clock()
{
    _updater.reset();
    assert(!_running);
}

void Clock::setTime() const
{
    _timeNS.store(fastos::ClockSteady::now() - fastos::SteadyTimeStamp::ZERO, std::memory_order_relaxed);
}

void
Clock::stop()
{
    _updater->stop();
}

FastOS_Runnable *
Clock::getRunnable() {
    return _updater.get();
}

}
