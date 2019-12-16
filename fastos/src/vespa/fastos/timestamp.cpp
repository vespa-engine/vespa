// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "timestamp.h"
#include <cmath>
#include <thread>
#include <sys/time.h>

using std::chrono::system_clock;
using std::chrono::steady_clock;
using std::chrono::nanoseconds;
using std::chrono::duration_cast;

namespace fastos {

const TimeStamp::TimeT TimeStamp::MILLI;
const TimeStamp::TimeT TimeStamp::MICRO;
const TimeStamp::TimeT TimeStamp::NANO;
const TimeStamp::TimeT TimeStamp::SEC;

using seconds = std::chrono::duration<double>;

std::string
TimeStamp::asString(double timeInSeconds)
{
    double intpart;
    double fractpart = std::modf(timeInSeconds, &intpart);
    time_t timeStamp = (time_t)intpart;
    struct tm timeStruct;
    gmtime_r(&timeStamp, &timeStruct);
    char timeString[128];
    strftime(timeString, sizeof(timeString), "%F %T", &timeStruct);
    char retval[160];
    uint32_t milliSeconds = std::min((uint32_t)(fractpart * 1000.0), 999u);
    snprintf(retval, sizeof(retval), "%s.%03u UTC", timeString, milliSeconds);
    return std::string(retval);
}

std::string
TimeStamp::asString(std::chrono::system_clock::time_point ns) {
    return asString(seconds(ns.time_since_epoch()).count());
}

time_t
time() {
    return system_clock::to_time_t(system_clock::now());
}

namespace {

SteadyTimeStamp
steady_now() {
    return SteadyTimeStamp(duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count());
}

}

StopWatch::StopWatch()
    : _startTime(steady_now())
{ }

void
StopWatch::restart() {
    _startTime = steady_now();
}

TimeStamp
StopWatch::elapsed() const {
    return (steady_now() - _startTime);
}

void
StopWatch::waitAtLeast(std::chrono::microseconds us, bool busyWait) {
    if (busyWait) {
        steady_clock::time_point deadline = steady_clock::now() + us;
        while (steady_clock::now() < deadline) {
            for (int i = 0; i < 1000; i++) { }
        }
    } else {
        std::this_thread::sleep_for(us);
    }
}

}
