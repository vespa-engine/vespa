// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>

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

constexpr duration from_s(double seconds) {
    return std::chrono::duration_cast<duration>(std::chrono::duration<double>(seconds));
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
    duration elapsed() const { return (steady_clock::now() - _start); }
};

}
