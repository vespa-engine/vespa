// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "logger.h"

#include <cstdlib>
#include <new> // for placement new

#define LOG_SETUP_INDIRECT(x, id)                            \
    static ns_log::Logger* ns_log_indirect_logger = nullptr; \
    static bool            logInitialised = false;           \
    static const char*     logName = x;                      \
    static const char*     indirectRcsId = id

#define LOG_INDIRECT_WOULD_LOG(levelName) ns_log_indirect_logger->wants(ns_log::Logger::levelName)

// Must use placement new in the following definition, since the variable
// "logger" must be a valid logger object DURING the construction of the
// logger object itself.
#define LOG_INDIRECT(level, ...)                                                                   \
    do {                                                                                           \
        if (!logInitialised) {                                                                     \
            logInitialised = true;                                                                 \
            ns_log_indirect_logger = static_cast<Logger*>(malloc(sizeof *ns_log_indirect_logger)); \
            new (ns_log_indirect_logger) Logger(logName, indirectRcsId);                           \
        }                                                                                          \
        if (LOG_INDIRECT_WOULD_LOG(level)) {                                                       \
            ns_log_indirect_logger->doLog(ns_log::Logger::level, __FILE__, __LINE__, __VA_ARGS__); \
        }                                                                                          \
    } while (false)
