// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <limits>
#include <string>

namespace fastos {

class TimeStamp
{
public:
    typedef int64_t TimeT;
    static const TimeT MILLI = 1000LL;
    static const TimeT MICRO = 1000*MILLI;
    static const TimeT NANO = 1000*MICRO;
    static const TimeT US = MILLI;
    static const TimeT MS = MICRO;
    static const TimeT SEC = NANO;
    static const TimeT MINUTE = 60*SEC;
    class Seconds {
    public:
        explicit Seconds(double v) : _v(v * NANO) {}
        TimeT val() const { return _v; }
    private:
        TimeT _v;
    };
    enum Special { FUTURE };
    TimeStamp() : _time(0)            { }
    TimeStamp(const timeval & tv) : _time(tv.tv_sec*SEC + tv.tv_usec*MILLI) { }
    TimeStamp(Special s) : _time(std::numeric_limits<TimeT>::max()) { (void) s; }
    TimeStamp(int v) : _time(v)   { }
    TimeStamp(unsigned int v) : _time(v)  { }
    TimeStamp(long v) : _time(v)   { }
    TimeStamp(unsigned long v) : _time(v)  { }
    TimeStamp(long long v) : _time(v)     { }
    TimeStamp(unsigned long long v) : _time(v)  { }
    TimeStamp(Seconds v) : _time(v.val())    { }
    TimeT val()                              const { return _time; }
    operator TimeT ()                        const { return val(); }
    TimeStamp & operator += (const TimeStamp & b)  { _time += b._time; return *this; }
    TimeStamp & operator -= (const TimeStamp & b)  { _time -= b._time; return *this; }
    TimeT time()                             const { return val()/NANO; }
    TimeT ms()                               const { return val()/1000000; }
    TimeT us()                               const { return val()/1000; }
    TimeT ns()                               const { return val(); }
    double sec()                             const { return val()/1000000000.0; }
    std::string toString()                   const { return asString(sec()); }
    static std::string asString(double timeInSeconds);
    static TimeStamp fromSec(double sec) { return Seconds(sec); }
private:
    TimeT _time;
};

inline TimeStamp operator +(const TimeStamp & a, const TimeStamp & b) { return TimeStamp(a.val() + b.val()); }
inline TimeStamp operator -(const TimeStamp & a, const TimeStamp & b) { return TimeStamp(a.val() - b.val()); }

class ClockSystem
{
public:
    static int64_t now();
};

class StopWatch
{
public:
    StopWatch() : _startTime(), _stopTime() { }

    void start();
    void stop();

    TimeStamp startTime() const { return _startTime; }

    TimeStamp elapsed() const {
        TimeStamp diff(_stopTime - _startTime);
        return (diff > 0) ? diff : TimeStamp(0);
    }
private:
    TimeStamp _startTime;
    TimeStamp _stopTime;
};

time_t time();

}

