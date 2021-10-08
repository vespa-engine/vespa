// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstring>
#include <cstdlib>

#if !__GNUC__ && !defined(__attribute__)
#define __attribute__(x)
#endif

namespace ns_log {

void throwInvalid(const char *fmt, ...)
    __attribute__((format(printf, 1, 2))) __attribute__((noreturn));

class InvalidLogException {
private:
    char *_what;
    InvalidLogException& operator = (const InvalidLogException&);

public:
    InvalidLogException(const InvalidLogException &x) :
        _what(strdup(x._what)) {}
    InvalidLogException(const char *s) : _what(strdup(s)) {}
    ~InvalidLogException() { free(_what); }
    const char *what() const { return _what; }
};

} // end namespace ns_log

