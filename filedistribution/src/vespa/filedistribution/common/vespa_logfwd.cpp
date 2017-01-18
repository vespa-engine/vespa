// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "logfwd.h"
#include <vector>
#include <vespa/log/log.h>

LOG_SETUP(".common.model");


using ns_log::Logger;

namespace {
    Logger::LogLevel toVespaLogLevel(filedistribution::logfwd::LogLevel level) {
        namespace l = filedistribution::logfwd;

        switch (level) {
            case l::info: return Logger::info;
            case l::debug: return Logger::debug;
            case l::error: return Logger::error;
            case l::warning: return Logger::warning;
            default:
                LOG(error, "Unknown log level, falling back to error");
                return Logger::error;
        }
    }
}

void filedistribution::logfwd::log_forward(LogLevel level, const char* file, int line, const char* fmt, ...)
{

    Logger::LogLevel vespaLogLevel = toVespaLogLevel(level);

    if (logger.wants(vespaLogLevel)) {
        const size_t maxSize(0x8000);
        std::vector<char> payload(maxSize);
        char * buf = &payload[0];

        va_list args;
        va_start(args, fmt);
        vsnprintf(buf, maxSize, fmt, args);
        va_end(args);

        logger.doLog(vespaLogLevel, file, line, "%s", buf);
    }
}
