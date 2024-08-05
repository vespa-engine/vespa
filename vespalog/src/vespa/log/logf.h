// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "log.h"
#include <version>

// TODO remove this once we're on a sufficiently new and shiny compiler version everywhere.
//  The LOGF() functionality--alongside any other std::format code--cannot be used until then.
#if defined(__cpp_lib_format)

// TODO `import std` once we have modules to avoid heavy <format> include
#include <format>

// Log using std::format with compile-time checking of format string.
// This is fully aware of std::string, std::string_view etc., so no more need for c_str().
#define LOGF(level, ...)                                         \
do {                                                             \
    if (LOG_WOULD_LOG(level)) [[unlikely]] {                     \
        ns_log::do_fmt_log(ns_log_logger, ns_log::Logger::level, \
                           __FILE__, __LINE__, __VA_ARGS__);     \
    }                                                            \
} while (false)

namespace ns_log {

void do_fmt_log_impl(Logger& logger, Logger::LogLevel level, const char* file, int line,
                     std::string_view fmt_str, std::format_args fmt_args);

// Proxy function for std::format-based logging which invokes compile-time parsing and
// validation of formatting string as well as type erased packing of formatting args.
template <typename... Args>
void do_fmt_log(Logger& logger, Logger::LogLevel level, const char* file, int line,
                std::format_string<Args...> fmt_str, Args&&... fmt_args)
{
    do_fmt_log_impl(logger, level, file, line, fmt_str.get(), std::make_format_args(fmt_args...));
}

}

#endif // defined(__cpp_lib_format)
