// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdarg>

namespace filedistribution {

/** To avoid requiring vespa log from the jni library*/
namespace logfwd {

enum LogLevel { debug, error, warning, info };

void log_forward(LogLevel level, const char *file, int line, const char *fmt, ...)
    __attribute__((format(printf,4,5)));

} //namespace logfwd
} //namespace filedistribution

#define LOGFWD(level, ...) filedistribution::logfwd::log_forward(filedistribution::logfwd::level, \
            __FILE__, __LINE__, __VA_ARGS__)


