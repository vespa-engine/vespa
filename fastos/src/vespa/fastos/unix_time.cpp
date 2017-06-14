// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.h"
#include "types.h"

double
FastOS_UNIX_Time::MicroSecs() const
{
    return ((1000.0 * 1000.0) * _time.tv_sec) + _time.tv_usec;
}

double
FastOS_UNIX_Time::MilliSecs() const
{
    return 1000.0 * _time.tv_sec + static_cast<double>(_time.tv_usec) / 1000.0;
}

double
FastOS_UNIX_Time::Secs() const
{
    return _time.tv_sec + static_cast<double>(_time.tv_usec) / 1000000.0;
}

FastOS_UNIX_Time&
FastOS_UNIX_Time::operator=(const FastOS_UNIX_Time& rhs)
{
    _time.tv_sec = rhs._time.tv_sec;
    _time.tv_usec = rhs._time.tv_usec;
    return *this;
}


FastOS_UNIX_Time&
FastOS_UNIX_Time::operator+=(const FastOS_UNIX_Time &rhs)
{
    struct timeval result;
    FASTOS_UNIX_TIMER_ADD(&_time, &rhs._time, &result);
    _time = result;
    return *this;
}


FastOS_UNIX_Time&
FastOS_UNIX_Time::operator-=(const FastOS_UNIX_Time &rhs)
{
    struct timeval result;
    FASTOS_UNIX_TIMER_SUB(&_time, &rhs._time, &result);
    _time = result;
    return *this;
}

void
FastOS_UNIX_Time::SetMicroSecs(double microsecs)
{
    if (microsecs > 0) {
        _time.tv_sec = static_cast<int>(microsecs / (1000 * 1000));
        _time.tv_usec = static_cast<int>(microsecs - (1000.0 * 1000.0) *
                _time.tv_sec);
    }
    else {
        _time.tv_sec = - static_cast<int>(- microsecs / (1000 * 1000));
        _time.tv_usec = - static_cast<int>(- microsecs -
                (1000.0 * 1000.0) *
                (- _time.tv_sec));
    }
}

void
FastOS_UNIX_Time::SetMilliSecs(double millisecs)
{
    if (millisecs > 0) {
        _time.tv_sec = static_cast<int>(millisecs/1000);
        _time.tv_usec = static_cast<int>((millisecs - 1000.0 * _time.tv_sec) *
                1000);
    }
    else {
        // some of the "-" may be unnecessary .
        // round on positive numbers to make sure conversion to int is ok.
        _time.tv_sec = - static_cast<int>(- millisecs / 1000);
        _time.tv_usec = - static_cast<int>((- millisecs - 1000.0 *
                                                   ( -_time.tv_sec)) * 1000);
    }
}


void
FastOS_UNIX_Time::SetSecs(double secs)
{
    if (secs > 0) {
        _time.tv_sec = static_cast<int>(secs);
        _time.tv_usec = static_cast<int>((secs - _time.tv_sec) * 1000000);
    }
    else {
        // some of the "-" may be unnecessary .
        // round on positive numbers to make sure conversion to int is ok.
        _time.tv_sec = - static_cast<int>(- secs);
        _time.tv_usec = - static_cast<int>((- secs - (-_time.tv_sec)) * 1000000);
    }
}

void FastOS_UNIX_Time::SetNow() {
    gettimeofday(&_time, NULL);
}

