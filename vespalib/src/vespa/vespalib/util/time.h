// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <vespa/vespalib/stllike/string.h>
#include <sys/time.h>

// Guidelines:
//
// If you want to store a time duration or take it as a parameter,
// prefer using vespalib::duration. This will allow automatic
// conversion for most input duration types while avoiding templates.
//
// When passing a verbatim time duration to a function, assigning it
// to a variable or comparing it to another time duration, prefer
// using chrono literals. This will greatly improve code readability.
//
// Avoid code that depends on the resolution of time
// durations. Specifically, do not use the count() function
// directly. Using the utility functions supplied below will both make
// your code safer (resolution independent) and simpler (avoiding
// duration_cast).
//
// Prefer using steady_clock, only use system_clock if you absolutely
// must have the system time.

using namespace std::literals::chrono_literals;

namespace vespalib {

using steady_clock = std::chrono::steady_clock;
using steady_time  = std::chrono::steady_clock::time_point;

using system_clock = std::chrono::system_clock;
using system_time  = std::chrono::system_clock::time_point;

using duration = std::chrono::nanoseconds;

constexpr double to_s(duration d) {
    return std::chrono::duration_cast<std::chrono::duration<double>>(d).count();
}

system_time to_utc(steady_time ts);

constexpr duration from_s(double seconds) {
    return std::chrono::duration_cast<duration>(std::chrono::duration<double>(seconds));
}

constexpr int64_t count_s(duration d) {
    return std::chrono::duration_cast<std::chrono::seconds>(d).count();
}

constexpr int64_t count_ms(duration d) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(d).count();
}

constexpr int64_t count_us(duration d) {
    return std::chrono::duration_cast<std::chrono::microseconds>(d).count();
}

constexpr int64_t count_ns(duration d) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(d).count();
}

constexpr duration from_timeval(const timeval & tv) {
    return duration(tv.tv_sec*1000000000L + tv.tv_usec*1000L);
}

constexpr duration from_timespec(const timespec & ts) {
    return duration(ts.tv_sec*1000000000L + ts.tv_nsec);
}

vespalib::string to_string(system_time time);

steady_time saturated_add(steady_time time, duration diff);

/**
 * Simple utility class used to measure how much time has elapsed
 * since it was constructed.
 **/
class Timer
{
private:
    steady_time _start;
public:
    Timer() : _start(steady_clock::now()) {}
    ~Timer();
    steady_time get_start() const { return _start; }
    duration elapsed() const { return (steady_clock::now() - _start); }
    static void waitAtLeast(duration dur, bool busyWait);
};

/**
 * The default frequency (1000hz) for vespa timer, with environment override VESPA_TIMER_HZ capped to [1..1000]
 */
uint32_t getVespaTimerHz();
duration adjustTimeoutByDetectedHz(duration timeout);
duration adjustTimeoutByHz(duration timeout, long hz);

}

#if defined(_LIBCPP_VERSION) && _LIBCPP_VERSION < 160000

// Temporary workaround until libc++ supports stream operators for duration

#include <iosfwd>

namespace std::chrono {

ostream& operator<<(ostream& os, const nanoseconds& value);

}
#endif
