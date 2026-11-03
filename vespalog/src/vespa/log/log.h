// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "logger.h"

/**
 * If this macro is defined, the regular LOG calls will go through the
 * buffered logger, using the whole messages as tokens.
 *
 * If this macro is not defined, only LOGB calls will go through log buffer
 */
// #define VESPA_LOG_USELOGBUFFERFORREGULARLOG 1

// Used to use anonymous namespaces, but they fail miserably in gdb 5.3

#define LOG_SETUP(...) static ns_log::Logger ns_log_logger(__VA_ARGS__) // NOLINT

#define LOG_WOULD_LOG(level) ns_log_logger.wants(ns_log::Logger::level)
#define LOG_WOULD_VLOG(level) ns_log_logger.wants(level)

// Define LOG if not using log buffer. Otherwise log buffer will define them
#ifndef VESPA_LOG_USELOGBUFFERFORREGULARLOG
#define LOG(level, ...)                                                                  \
    do {                                                                                 \
        if (__builtin_expect(LOG_WOULD_LOG(level), false)) {                             \
            ns_log_logger.doLog(ns_log::Logger::level, __FILE__, __LINE__, __VA_ARGS__); \
        }                                                                                \
    } while (false)

#define VLOG(level, ...)                                                 \
    do {                                                                 \
        if (__builtin_expect(LOG_WOULD_VLOG(level), false)) {            \
            ns_log_logger.doLog(level, __FILE__, __LINE__, __VA_ARGS__); \
        }                                                                \
    } while (false)
#endif

#define EV_STARTING(name)                        \
    do {                                         \
        if (LOG_WOULD_LOG(event)) {              \
            ns_log_logger.doEventStarting(name); \
        }                                        \
    } while (false)

#define EV_STOPPING(name, why)                        \
    do {                                              \
        if (LOG_WOULD_LOG(event)) {                   \
            ns_log_logger.doEventStopping(name, why); \
        }                                             \
    } while (false)

#define EV_STARTED(name)                        \
    do {                                        \
        if (LOG_WOULD_LOG(event)) {             \
            ns_log_logger.doEventStarted(name); \
        }                                       \
    } while (false)

#define EV_STOPPED(name, pid, exitcode)                        \
    do {                                                       \
        if (LOG_WOULD_LOG(event)) {                            \
            ns_log_logger.doEventStopped(name, pid, exitcode); \
        }                                                      \
    } while (false)

#define EV_CRASH(name, pid, signal)                        \
    do {                                                   \
        if (LOG_WOULD_LOG(event)) {                        \
            ns_log_logger.doEventCrash(name, pid, signal); \
        }                                                  \
    } while (false)

#define EV_PROGRESS(name, ...)                                \
    do {                                                      \
        if (LOG_WOULD_LOG(event)) {                           \
            ns_log_logger.doEventProgress(name, __VA_ARGS__); \
        }                                                     \
    } while (false)

#define EV_COUNT(name, value)                        \
    do {                                             \
        if (LOG_WOULD_LOG(event)) {                  \
            ns_log_logger.doEventCount(name, value); \
        }                                            \
    } while (false)

#define EV_VALUE(name, value)                        \
    do {                                             \
        if (LOG_WOULD_LOG(event)) {                  \
            ns_log_logger.doEventValue(name, value); \
        }                                            \
    } while (false)

#define EV_STATE(name, value)                        \
    do {                                             \
        if (LOG_WOULD_LOG(event)) {                  \
            ns_log_logger.doEventState(name, value); \
        }                                            \
    } while (false)

namespace ns_log {

[[noreturn]] extern void log_assert_fail(const char* assertion, const char* file, uint32_t line);

[[noreturn]] extern void log_abort(const char* message, const char* file, uint32_t line);

} // namespace ns_log

//======================================//
// LOG_ASSERT and LOG_ABORT definitions //
//======================================//

#ifndef __STRING
#define __STRING(x) #x
#endif

#define LOG_ABORT(msg) (ns_log::log_abort(msg, __FILE__, __LINE__))

// LOG_ASSERT is always active, regardless of NDEBUG
#define LOG_ASSERT(expr) ((void)((expr) ? 0 : (ns_log::log_assert_fail(__STRING(expr), __FILE__, __LINE__), 0)))
