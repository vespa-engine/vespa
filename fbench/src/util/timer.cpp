// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "timer.h"
#include <stdio.h>
#include <thread>

Timer::Timer()
    : _time(),
      _timespan(0),
      _maxTime(0),
      _running(false)
{
}

void
Timer::SetMax(double max)
{
    _maxTime = max;
}

void
Timer::Start()
{
    if (_running)
        return;
    _running = true;
    _time = clock::now();
}

void
Timer::Stop()
{
    if (!_running)
        return;
    _timespan = GetCurrent();
    _running = false;
}

void
Timer::Clear()
{
    _running  = false;
    _timespan = 0;
}

double
Timer::GetTimespan()
{
    if (_running)
        Stop();
    return _timespan;
}

double
Timer::GetRemaining()
{
    double span = GetTimespan();
    return (span < _maxTime) ? _maxTime - span : 0;
}

double
Timer::GetCurrent()
{
    if (!_running)
        return 0;
    using milliseconds = std::chrono::duration<double, std::milli>;
    return std::chrono::duration_cast<milliseconds>(time_point(clock::now()) - _time).count();
}

void
Timer::TestClass()
{
    Timer test;

    printf("*** Start Testing: class Timer ***\n");
    printf("set max time to 5 seconds, then sleep for 1...\n");
    test.SetMax(5000);
    test.Start();
    std::this_thread::sleep_for(std::chrono::seconds(1));
    test.Stop();
    printf("elapsed: %f, left:%f\n",
           test.GetTimespan(), test.GetRemaining());
    printf("set max time to 1 second, then sleep for 2...\n");
    test.SetMax(1000);
    test.Start();
    std::this_thread::sleep_for(std::chrono::seconds(2));
    test.Stop();
    printf("elapsed: %f, left:%f\n",
           test.GetTimespan(), test.GetRemaining());
    printf("*** Finished Testing: class Timer ***\n");
}
