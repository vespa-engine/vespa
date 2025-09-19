// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <format>
#include <string>

namespace vespalib {

namespace slime { struct Inserter; }

/**
 * Interface for reporting initialization status into a slime.
 */
struct InitializationStatusProducer {
    virtual ~InitializationStatusProducer() = default;
    virtual void report_initialization_status(const vespalib::slime::Inserter &inserter) const = 0;


    using time_point = std::chrono::system_clock::time_point;

    static std::string timepoint_to_string(time_point tp) {
        time_t secs = std::chrono::duration_cast<std::chrono::seconds>(tp.time_since_epoch()).count();
        uint32_t usecs_part = std::chrono::duration_cast<std::chrono::microseconds>(tp.time_since_epoch()).count() % 1000000;
        return std::format("{}.{:06}", secs, usecs_part);
    }

};

} // namespace vespalib
