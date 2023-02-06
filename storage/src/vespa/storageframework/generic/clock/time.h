// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>

namespace vespalib {
    class asciistream;
}

namespace storage::framework {

using MonotonicTimePoint = vespalib::steady_time;
using MonotonicDuration = vespalib::duration;

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

/**
 * Class containing common functionality for the various time instances. Try to
 * make time instances as easy to use as possible, without creating risk of
 * automatic conversion between time types.
 */
template<typename Type, int MicrosPerUnit>
class Time
{
    uint64_t _time; // time_t may be signed. Negative timestamps is just a
                    // source for bugs. Enforce unsigned.

protected:
    explicit Time(uint64_t t) : _time(t) {}

public:
    [[nodiscard]] uint64_t getTime() const { return _time; }

    Type& operator-=(const Type& o) { _time -= o._time; return static_cast<Type&>(*this); }
    Type& operator+=(const Type& o) { _time += o._time; return static_cast<Type&>(*this); }
    bool operator<(const Type& o) const { return (_time < o._time); }
    bool operator<=(const Type& o) const { return (_time <= o._time); }
    bool operator>=(const Type& o) const { return (_time >= o._time); }
    bool operator>(const Type& o) const { return (_time > o._time); }
    bool operator==(const Type& o) const { return (_time == o._time); }
    Type& operator++() { ++_time; return static_cast<Type&>(*this); }
    Type& operator--() { --_time; return *this; }

    [[nodiscard]] vespalib::string toString(TimeFormat timeFormat = DATETIME) const {
        return getTimeString(_time * MicrosPerUnit, timeFormat);
    }

    static Type max() { return Type(std::numeric_limits<uint64_t>::max()); }

};

template<typename Type, int MPU>
std::ostream& operator<<(std::ostream& out, const Time<Type, MPU>& t);

template<typename Type, int MPU>
vespalib::asciistream& operator<<(vespalib::asciistream& out, const Time<Type, MPU>& t);

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

    [[nodiscard]] SecondTime getSeconds() const { return SecondTime(getTime() / 1000000); }
};

}
