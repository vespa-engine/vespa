// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <cstdlib>

#if !__GNUC__ && !defined(__attribute__)
#define __attribute__(x)
#endif

namespace ns_log {

void throwInvalid(const char *fmt, ...)
    __attribute__((format(printf, 1, 2))) __attribute__((noreturn));

class InvalidLogException {
private:
    std::string _what;

public:
    InvalidLogException& operator = (const InvalidLogException&) = delete;
    InvalidLogException(const InvalidLogException &x) = default;
    InvalidLogException(const char *s) : _what(s) {}
    ~InvalidLogException() = default;
    const char *what() const { return _what.c_str(); }
};

} // end namespace ns_log

