// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/operators.hpp>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>

namespace vespalib {
    class asciistream;
}

namespace storage::framework {

using MonotonicTimePoint = std::chrono::steady_clock::time_point;
using MonotonicDuration = std::chrono::steady_clock::duration;

struct Clock;

enum TimeFormat {
    DATETIME               = 0x01, // 2010-04-26 19:23:03
    DATETIME_WITH_MILLIS   = 0x02, // 2010-04-26 19:23:03.001
    DATETIME_WITH_MICROS   = 0x04, // 2010-04-26 19:23:03.001023
    DATETIME_ALL           = 0x07,
    DIFFERENCE             = 0x10, // 1 day, 4 hours, 43 minutes and 3 seconds
    DIFFERENCE_WITH_MICROS = 0x20, // 1 day, 4 hours, 43 minutes, 3 seconds and 123123 microseconds
    DIFFERENCE_ALL         = 0x30
};

/**
 * Utility function used by Time instances (to avoid implementation in
 * header file).
 */
vespalib::string getTimeString(uint64_t microSecondTime, TimeFormat format);

// TODO deprecate framework time point and duration classes in favor of
// using std::chrono.

// As this class can't include clock, this utility function can be used in
// header implementation to get actual time.
uint64_t getRawMicroTime(const Clock&);

/**
 * Class containing common functionality for the various time instances. Try to
 * make time instances as easy to use as possible, without creating risk of
 * automatic conversion between time types.
 */
template<typename Type, int MicrosPerUnit>
class Time : public boost::operators<Type>
{
    uint64_t _time; // time_t may be signed. Negative timestamps is just a
                    // source for bugs. Enforce unsigned.

protected:
    Time(uint64_t t) : _time(t) {}

public:
    uint64_t getTime() const { return _time; }
    void setTime(uint64_t t) { _time = t; }
    bool isSet() const { return (_time != 0); }

    Type& operator-=(const Type& o)
        { _time -= o._time; return static_cast<Type&>(*this); }
    Type& operator+=(const Type& o)
        { _time += o._time; return static_cast<Type&>(*this); }
    bool operator<(const Type& o) const { return (_time < o._time); }
    bool operator==(const Type& o) const { return (_time == o._time); }
    Type& operator++() { ++_time; return static_cast<Type&>(*this); }
    Type& operator--() { --_time; return *this; }

    Type getDiff(const Type& o) const {
        return Type(_time > o._time ? _time - o._time : o._time - _time);
    }

    vespalib::string toString(TimeFormat timeFormat = DATETIME) const {
        return getTimeString(_time * MicrosPerUnit, timeFormat);
    }

    static Type max() { return Type(std::numeric_limits<uint64_t>().max()); }
    static Type min() { return Type(0); }

};

template<typename Type, typename Number>
Type& operator/(Type& type, Number n) {
    type.setTime(type.getTime() / n);
    return type;
}

template<typename Type, typename Number>
Type& operator*(Type& type, Number n) {
    type.setTime(type.getTime() * n);
    return type;
}

template<typename Type, int MPU>
std::ostream& operator<<(std::ostream& out, const Time<Type, MPU>& t);

template<typename Type, int MPU>
vespalib::asciistream& operator<<(vespalib::asciistream& out, const Time<Type, MPU>& t);

struct MicroSecTime;
struct MilliSecTime;

/**
 * \class storage::framework::SecondTime
 * \ingroup clock
 *
 * \brief Wrapper class for a timestamp in seconds.
 *
 * To prevent errors where one passes time in one granularity to a function
 * requiring time in another granularity. This little wrapper class exist to
 * make sure that will conflict in types
 */
struct SecondTime : public Time<SecondTime, 1000000> {
    explicit SecondTime(uint64_t t = 0) : Time<SecondTime, 1000000>(t) {}
    explicit SecondTime(const Clock& clock)
        : Time<SecondTime, 1000000>(getRawMicroTime(clock) / 1000000) {}

    MilliSecTime getMillis() const;
    MicroSecTime getMicros() const;
};

/**
 * \class storage::framework::MilliSecTime
 * \ingroup clock
 *
 * \brief Wrapper class for a timestamp in milliseconds.
 *
 * To prevent errors where one passes time in one granularity to a function
 * requiring time in another granularity. This little wrapper class exist to
 * make sure that will conflict in types
 */
struct MilliSecTime : public Time<MilliSecTime, 1000> {
    explicit MilliSecTime(uint64_t t = 0) : Time<MilliSecTime, 1000>(t) {}
    explicit MilliSecTime(const Clock& clock)
        : Time<MilliSecTime, 1000>(getRawMicroTime(clock) / 1000) {}

    SecondTime getSeconds() const { return SecondTime(getTime() / 1000); }
    MicroSecTime getMicros() const;
};

/**
 * \class storage::framework::MicroSecTime
 * \ingroup clock
 *
 * \brief Wrapper class for a timestamp in seconds.
 *
 * To prevent errors where one passes time in one granularity to a function
 * requiring time in another granularity. This little wrapper class exist to
 * make sure that will conflict in types
 */
struct MicroSecTime : public Time<MicroSecTime, 1> {
    explicit MicroSecTime(uint64_t t = 0) : Time<MicroSecTime, 1>(t) {}
    explicit MicroSecTime(const Clock& clock)
        : Time<MicroSecTime, 1>(getRawMicroTime(clock)) {}

    MilliSecTime getMillis() const { return MilliSecTime(getTime() / 1000); }
    SecondTime getSeconds() const { return SecondTime(getTime() / 1000000); }
};

inline MilliSecTime SecondTime::getMillis() const {
    return MilliSecTime(getTime() * 1000);
}

inline MicroSecTime SecondTime::getMicros() const {
    return MicroSecTime(getTime() * 1000 * 1000);
}

inline MicroSecTime MilliSecTime::getMicros() const {
    return MicroSecTime(getTime() * 1000);
}

}
