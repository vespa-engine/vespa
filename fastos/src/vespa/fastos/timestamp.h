// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <limits>
#include <string>
#include <chrono>

namespace fastos {

class TimeStamp
{
public:
    typedef int64_t TimeT;
    static const TimeT MILLI = 1000LL;
    static const TimeT MICRO = 1000*MILLI;
    static const TimeT NANO = 1000*MICRO;
    static const TimeT SEC = NANO;
    TimeStamp() : _time(0)            { }
    TimeStamp(const timeval & tv) : _time(tv.tv_sec*SEC + tv.tv_usec*MILLI) { }
    TimeStamp(int v) : _time(v)   { }
    TimeStamp(unsigned int v) : _time(v)  { }
    TimeStamp(long v) : _time(v)   { }
    TimeStamp(unsigned long v) : _time(v)  { }
    TimeStamp(long long v) : _time(v)     { }
    TimeStamp(unsigned long long v) : _time(v)  { }
    TimeT val()                      const { return _time; }
    operator TimeT ()                const { return val(); }
    TimeStamp & operator += (TimeStamp b)  { _time += b._time; return *this; }
    TimeStamp & operator -= (TimeStamp b)  { _time -= b._time; return *this; }
    TimeT time()                     const { return val()/NANO; }
    TimeT ms()                       const { return val()/1000000; }
    TimeT us()                       const { return val()/1000; }
    TimeT ns()                       const { return val(); }
    double sec()                     const { return val()/1000000000.0; }
    std::string toString()           const { return asString(sec()); }
    static std::string asString(double timeInSeconds);
    static std::string asString(std::chrono::system_clock::time_point time);
private:
    TimeT _time;
};

inline TimeStamp operator +(TimeStamp a, TimeStamp b) { return TimeStamp(a.val() + b.val()); }
inline TimeStamp operator -(TimeStamp a, TimeStamp b) { return TimeStamp(a.val() - b.val()); }
inline TimeStamp operator *(long a, TimeStamp b) { return TimeStamp(a * b.val()); }
inline TimeStamp operator *(double a, TimeStamp b) { return TimeStamp(static_cast<int64_t>(a * b.val())); }

time_t time();

}

