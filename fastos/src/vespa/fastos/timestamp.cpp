// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "timestamp.h"
#include <chrono>
#include <cmath>
#include <thread>
#include <sys/time.h>

using namespace std::chrono;

namespace fastos {

const TimeStamp::TimeT TimeStamp::MILLI;
const TimeStamp::TimeT TimeStamp::MICRO;
const TimeStamp::TimeT TimeStamp::NANO;
const TimeStamp::TimeT TimeStamp::US;
const TimeStamp::TimeT TimeStamp::MS;
const TimeStamp::TimeT TimeStamp::SEC;
const TimeStamp::TimeT TimeStamp::MINUTE;

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

UTCTimeStamp
ClockSystem::now()
{
    struct timeval timeNow;
    gettimeofday(&timeNow, nullptr);
    int64_t ns = timeNow.tv_sec;
    ns *= TimeStamp::NANO;
    ns += timeNow.tv_usec*1000;
    return UTCTimeStamp(ns);
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

std::ostream &
operator << (std::ostream & os, UTCTimeStamp ts) {
    return os << ts.toString();
}

std::ostream &
operator << (std::ostream & os, SteadyTimeStamp ts) {
    return os << ts.toString();
}

SteadyTimeStamp
ClockSteady::now()
{
    return steady_now();
}

const SteadyTimeStamp SteadyTimeStamp::ZERO;
const SteadyTimeStamp SteadyTimeStamp::FUTURE(TimeStamp::FUTURE);
const UTCTimeStamp UTCTimeStamp::ZERO;
const UTCTimeStamp UTCTimeStamp::FUTURE(TimeStamp::FUTURE);

UTCTimeStamp
SteadyTimeStamp::toUTC() const {
    UTCTimeStamp nowUtc = ClockSystem::now();
    SteadyTimeStamp nowSteady = ClockSteady::now();
    return nowUtc - (nowSteady - *this);
}

StopWatch::StopWatch()
    : _startTime(steady_now()),
      _stopTime(_startTime)
{ }

void
StopWatch::restart() {
    _startTime = steady_now();
    _stopTime = _startTime;
}

StopWatch &
StopWatch::stop()  {
    _stopTime = steady_now();
    return *this;
}

void
StopWatch::waitAtLeast(std::chrono::microseconds us, bool busyWait) {
    steady_clock::time_point startTime = steady_clock::now();
    steady_clock::time_point deadline = startTime + us;
    while (steady_clock::now() < deadline) {
        if (busyWait) {
            for (int i = 0; i < 1000; i++)
                ;
        } else {
            microseconds rem = (us - duration_cast<microseconds>(steady_clock::now() - startTime));
            std::this_thread::sleep_for(rem);
        }
    }
}

}
