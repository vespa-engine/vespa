// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <cstdlib>
#include <chrono>

namespace ns_log {

[[noreturn]] void throwInvalid(const char *fmt, ...) __attribute__((format(printf, 1, 2)));

class InvalidLogException {
private:
    std::string _what;

public:
    InvalidLogException& operator = (const InvalidLogException&) = delete;
    InvalidLogException(const InvalidLogException &x) = default;
    explicit InvalidLogException(const char *s) : _what(s) {}
    ~InvalidLogException() = default;
    [[nodiscard]] const char *what() const { return _what.c_str(); }
};

using system_time = std::chrono::system_clock::time_point;
using duration = system_time::duration;

constexpr int64_t
count_s(duration d) noexcept {
    return std::chrono::duration_cast<std::chrono::seconds>(d).count();
}

constexpr int64_t
count_us(duration d) noexcept {
    return std::chrono::duration_cast<std::chrono::microseconds>(d).count();
}

// XXX this is way too complicated, must be some simpler way to do this
/** Timer class used to retrieve timestamp, such that we can override in test */
struct Timer {
    virtual ~Timer() = default;
    virtual system_time getTimestamp() const noexcept;
};

} // end namespace ns_log
