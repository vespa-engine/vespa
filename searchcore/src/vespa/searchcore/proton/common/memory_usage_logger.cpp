// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_usage_logger.h"
#include <vespa/vespalib/util/process_memory_stats.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.memory_usage_logger");

namespace proton {

void
MemoryUsageLogger::log(const std::string& step, const std::string& label)
{
    if (LOG_WOULD_LOG(debug)) {
        auto stats = vespalib::ProcessMemoryStats::create(0.01);
        LOG(debug, "Memory usage for %s %s: %" PRIu64, step.c_str(), label.c_str(),
            stats.getAnonymousRss());
    }
}

}
